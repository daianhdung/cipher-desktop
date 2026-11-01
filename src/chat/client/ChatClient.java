package chat.client;

import chat.common.*;
import common.*;
import javax.crypto.SecretKey;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/** UI-independent v2 client. Public network methods must be called off the EDT. */
public final class ChatClient implements AutoCloseable {
    private final Consumer<Packet> events;
    private final Path inbox;
    private final Map<String,CompletableFuture<Packet>> pending=new ConcurrentHashMap<>();
    private final Map<String,Incoming> incoming=new ConcurrentHashMap<>();
    private final Object sending=new Object();
    private volatile Socket socket;
    private volatile Channel channel;
    private volatile SecretKey key;
    private volatile SecretKey rotating;
    private PublicKey serverKey;
    private volatile boolean connected;
    private volatile boolean intentional;
    private ScheduledExecutorService heartbeat;
    private String username,session;
    private final class Incoming implements AutoCloseable {
        final Packet start; final Path part,done; final OutputStream out; final MessageDigest sha;
        long bytes; int index; int percent=-1;
        Incoming(Packet p) throws Exception {
            start=p; part=Files.createTempFile(inbox,"receiving-",".part");
            done=inbox.resolve(UUID.randomUUID()+"_"+safeName(p.text()));
            out=new BufferedOutputStream(Files.newOutputStream(part)); sha=MessageDigest.getInstance("SHA-256");
        }
        public void close() throws IOException { out.close(); Files.deleteIfExists(part); }
    }
    public ChatClient(Path inbox,Consumer<Packet> events) { this.inbox=inbox; this.events=events; }
    public boolean connected() { return connected; }
    public String username() { return username; }
    public static String safeName(String name) {
        String s=name.replaceAll("[^\\p{L}\\p{N}._ -]","_");
        return s.isBlank() || s.equals(".") || s.equals("..")?"attachment.bin":s.substring(0,Math.min(180,s.length()));
    }
    private void emit(Packet p) { events.accept(p); }
    public void connect(String host,int port,String user,String enteredKey) throws Exception {
        if(connected || socket!=null) throw new IOException("Hãy tạo phiên Client mới sau khi ngắt");
        Files.createDirectories(inbox); username=user; intentional=false;
        Socket s=new Socket(); socket=s;
        try {
            s.connect(new InetSocketAddress(host,port),5000); s.setSoTimeout(10000); s.setTcpNoDelay(true);
            var in=new DataInputStream(new BufferedInputStream(s.getInputStream()));
            var out=new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));
            Packet hello=Wire.plain(in);
            if(!hello.type().equals("HELLO") || hello.size()!=Wire.VERSION || !hello.text().equals("AESCHAT/2")) throw new IOException("Server không hỗ trợ AESCHAT/2");
            session=hello.id(); serverKey=RSAUtil.decodePublic(hello.data());
            key=enteredKey.isEmpty()?Crypto.randomKey():AESUtil.taoKhoa(enteredKey);
            Wire.plain(out,new Packet("JOIN","","","",user,"",Wire.VERSION,0,RSAUtil.bocKhoaAES(key,serverKey)));
            channel=new Channel(s,in,out,key,session,false);
            Packet joined=channel.read();
            if(!joined.type().equals("JOINED")) throw new IOException(joined.text());
            connected=true; s.setSoTimeout(120000);
            emit(Packet.event("CONNECTED",session,"","",user,hello.extra()));
            heartbeat=Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().factory());
            heartbeat.scheduleAtFixedRate(()-> { try { send(Packet.of("PING","","","")); } catch(Exception e) { close(); } },20,20,TimeUnit.SECONDS);
            Thread.ofVirtual().name("chat-reader-"+user).start(this::readLoop);
        } catch(Exception e) { connected=false; s.close(); if(channel!=null) channel.close(); if(heartbeat!=null) heartbeat.shutdownNow(); key=null; throw e; }
    }
    private void send(Packet p) throws IOException { synchronized(sending) { if(!connected) throw new IOException("Chưa kết nối"); channel.send(p); } }
    private Packet request(Packet p) throws Exception {
        CompletableFuture<Packet> answer=new CompletableFuture<>(); pending.put(p.id(),answer);
        try { send(p); return answer.get(15,TimeUnit.SECONDS); }
        finally { pending.remove(p.id()); }
    }
    private static void validateText(String text) {
        if(text.isBlank() || text.getBytes(StandardCharsets.UTF_8).length>8192) throw new IllegalArgumentException("Văn bản cần có nội dung, tối đa 8192 byte UTF-8");
    }
    public String text(String to,String text) throws Exception {
        validateText(text); String id=UUID.randomUUID().toString(); send(Packet.of("TEXT",id,to,text)); return id;
    }
    public String lab(String text) throws Exception {
        validateText(text); String id=UUID.randomUUID().toString(); send(Packet.of("LAB",id,"",text)); return id;
    }
    public void history(String peer) throws Exception { send(Packet.of("HISTORY",UUID.randomUUID().toString(),peer,"")); }
    public void contacts() throws Exception { send(Packet.of("CONTACTS","","","")); }
    public void download(String id) throws Exception { send(Packet.of("FILE_GET",id,"","")); }
    public String preview(String text) throws Exception {
        validateText(text);
        if(key==null) throw new IOException("Chưa có khóa");
        return Base64.getEncoder().encodeToString(Crypto.seal(key,text.getBytes(StandardCharsets.UTF_8),"preview"));
    }
    public void rotate(String phrase) throws Exception {
        if(phrase.isBlank()) throw new IllegalArgumentException("Nhập khóa trong Lab");
        synchronized(sending) {
            SecretKey next=AESUtil.taoKhoa(phrase); rotating=next; channel.expectKey(next);
            String id=UUID.randomUUID().toString();
            try { request(new Packet("ROTATE",id,"","","","",0,0,RSAUtil.bocKhoaAES(next,serverKey))); }
            catch(Exception e) { close(); throw e; }
        }
    }
    public void file(String to,Path source,AtomicBoolean cancel) throws Exception {
        long size=Files.size(source);
        if(size>Wire.MAX_FILE || !Files.isRegularFile(source)) throw new IOException("Chỉ nhận file thường tối đa 1 GiB");
        Packet ready=request(new Packet("FILE_BEGIN",UUID.randomUUID().toString(),"",to,source.getFileName().toString(),"",size,0,null));
        String id=ready.text(); MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(var input=new BufferedInputStream(Files.newInputStream(source))) {
            byte[] buf=new byte[Wire.CHUNK]; int n,index=0,percent=-1; long bytes=0;
            while((n=input.read(buf))!=-1) {
                if(cancel.get()) { send(Packet.of("FILE_CANCEL",id,"","")); return; }
                byte[] data=Arrays.copyOf(buf,n); digest.update(data);
                send(new Packet("FILE_CHUNK",id,"",to,"","",0,index++,data)); bytes+=n;
                int nextPercent=(int)(bytes*100/Math.max(1,size));
                if(nextPercent!=percent) { percent=nextPercent; emit(new Packet("UPLOAD_PROGRESS",id,username,to,source.getFileName().toString(),"",size,percent,null)); }
            }
            if(cancel.get()) send(Packet.of("FILE_CANCEL",id,"",""));
            else send(Packet.of("FILE_END",id,"",AESUtil.hex(digest.digest())));
        } catch(Exception e) { try { send(Packet.of("FILE_CANCEL",id,"","")); } catch(Exception ignored) {} throw e; }
    }
    private void readLoop() {
        String error="";
        try {
            while(connected) {
                Packet p=channel.read();
                if(p.type().equals("ROTATED")) {
                    SecretKey next=rotating;
                    if(next==null) throw new IOException("ACK đổi khóa không mong đợi");
                    channel.clientCommitKey(next); key=next; rotating=null;
                }
                CompletableFuture<Packet> future=pending.get(p.id());
                if(future!=null) {
                    if(p.type().equals("ERROR")) future.completeExceptionally(new IOException(p.text()));
                    else if(Set.of("FILE_READY","ROTATED").contains(p.type())) future.complete(p);
                }
                if(p.type().startsWith("FILE_")) receiveFile(p);
                if(!p.type().equals("PONG") && !Set.of("FILE_DATA","FILE_START","FILE_FINISH").contains(p.type())) emit(p);
            }
        } catch(Exception e) { error=e.getMessage()==null?e.getClass().getSimpleName():e.getMessage(); }
        finally {
            connected=false; if(channel!=null) channel.close();
            if(heartbeat!=null) heartbeat.shutdownNow();
            for(var f:pending.values()) f.completeExceptionally(new IOException("Kết nối đóng"));
            for(var f:incoming.values()) try { f.close(); } catch(Exception ignored) {}
            incoming.clear(); key=null; rotating=null;
            emit(Packet.of("DISCONNECTED","","",intentional?"Đã ngắt":"Mất kết nối: "+error));
        }
    }
    private void receiveFile(Packet p) throws Exception {
        switch(p.type()) {
            case "FILE_START" -> {
                if(incoming.size()>=4 || incoming.containsKey(p.id()) || p.size()<0 || p.size()>Wire.MAX_FILE || !p.extra().matches("[0-9a-f]{64}")) throw new IOException("Metadata file không hợp lệ");
                incoming.put(p.id(),new Incoming(p));
                emit(new Packet("DOWNLOAD_PROGRESS",p.id(),p.from(),p.to(),p.text(),"",p.size(),0,null));
            }
            case "FILE_DATA" -> {
                Incoming f=incoming.get(p.id());
                if(f==null || p.index()!=f.index++ || p.data().length==0 || f.bytes+p.data().length>f.start.size()) throw new IOException("Chunk download không hợp lệ");
                f.out.write(p.data()); f.sha.update(p.data()); f.bytes+=p.data().length;
                int percent=(int)(f.bytes*100/Math.max(1,f.start.size()));
                if(percent!=f.percent) { f.percent=percent; emit(new Packet("DOWNLOAD_PROGRESS",p.id(),f.start.from(),f.start.to(),f.start.text(),"",f.start.size(),percent,null)); }
            }
            case "FILE_FINISH" -> {
                Incoming f=incoming.remove(p.id()); if(f==null) throw new IOException("File kết thúc không mong đợi");
                try {
                    f.out.close(); String hash=AESUtil.hex(f.sha.digest());
                    if(f.bytes!=f.start.size() || !hash.equals(f.start.extra()) || !hash.equals(p.text())) {
                        // Reader must never wait for the rotation lock: enqueue ACK directly.
                        ackLater(p.id(),"FAILED");
                        throw new IOException("SHA-256 download không khớp");
                    }
                    Files.move(f.part,f.done,StandardCopyOption.ATOMIC_MOVE);
                    ackLater(p.id(),hash);
                    emit(new Packet("FILE_RECEIVED",p.id(),f.start.from(),f.start.to(),f.start.text(),f.done.toAbsolutePath().toString(),f.bytes,100,null));
                } finally { f.close(); }
            }
            case "FILE_ABORT" -> { Incoming f=incoming.remove(p.id()); if(f!=null) f.close(); }
            default -> { }
        }
    }
    private void ackLater(String id,String hash) {
        Thread.ofVirtual().start(()-> { try { send(Packet.of("FILE_ACK",id,"",hash)); } catch(Exception ignored) {} });
    }
    /** Closing a socket is immediate; never wait for the writer or a transfer lock. */
    @Override public void close() {
        intentional=true; connected=false;
        if(channel!=null) channel.close();
        Socket s=socket; if(s!=null) try { s.close(); } catch(IOException ignored) {}
        if(heartbeat!=null) heartbeat.shutdownNow();
    }
}
