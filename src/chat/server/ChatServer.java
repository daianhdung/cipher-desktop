package chat.server;

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

/** Multi-user extension; MockServer remains the isolated legacy v1 implementation. */
public final class ChatServer implements AutoCloseable {
    private final ServerSocket listener=new ServerSocket();
    private final ChatRepository repository;
    private final KeyPair rsa=RSAUtil.taoCapKhoa();
    private final ConcurrentMap<String,Session> online=new ConcurrentHashMap<>();
    private final Set<Socket> connections=ConcurrentHashMap.newKeySet();
    private final Map<String,SecretKey> historyKeys=Collections.synchronizedMap(new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<String,SecretKey> e) { return size()>1024; }
    });
    private final Path storage;
    private final ExecutorService workers=Executors.newVirtualThreadPerTaskExecutor();
    private final Semaphore capacity=new Semaphore(100);
    private volatile boolean closed;
    private final AtomicBoolean shutdown=new AtomicBoolean();
    public ChatServer(String host,int port,Path directory) throws Exception {
        storage=directory.toAbsolutePath().normalize(); Files.createDirectories(storage.resolve("files"));
        // Reject an occupied port before modifying any persisted session state.
        listener.setReuseAddress(true); listener.bind(new InetSocketAddress(host,port));
        ChatRepository opened=null;
        try {
            opened=new ChatRepository(storage.resolve("chat.db"));
            // A crash leaves only .part files; completed attachments are retained.
            try(var files=Files.list(storage.resolve("files"))) {
                for(Path f:files.filter(p->p.getFileName().toString().endsWith(".part")).toList()) Files.deleteIfExists(f);
            }
            repository=opened;
        } catch(Exception e) {
            listener.close(); if(opened!=null) opened.close(); throw e;
        }
    }
    public int port() { return listener.getLocalPort(); }
    public int onlineCount() { return online.size(); }
    public List<String> currentSessions() {
        return online.values().stream().map(s->s.user+" | "+s.id+" | "+s.socket.getRemoteSocketAddress()).sorted().toList();
    }
    public void run() throws IOException {
        log("RUNNING "+listener.getLocalSocketAddress()+" | database="+storage.resolve("chat.db")+" | RSA="+RSAUtil.fingerprint(rsa.getPublic()));
        while(!closed) {
            try {
                Socket s=listener.accept();
                if(!capacity.tryAcquire()) { s.close(); continue; }
                connections.add(s);
                try {
                    workers.submit(()-> { try { new Session(s).run(); } finally { connections.remove(s); capacity.release(); } });
                } catch(RejectedExecutionException e) {
                    connections.remove(s); capacity.release(); s.close(); if(!closed) throw e;
                }
            } catch(SocketException e) { if(!closed) throw e; }
        }
    }
    private void log(String s) { System.out.println(ChatRepository.now()+" | online="+online.size()+" | "+s); }
    private void broadcastUsers() {
        String names=String.join("\n",new TreeSet<>(online.keySet()));
        for(Session s:online.values()) s.offer(Packet.of("USERS","","",names));
    }
    private final class Upload implements AutoCloseable {
        final String id,to,name; final long size; final Path part,done;
        final MessageDigest digest; final OutputStream out;
        long bytes; int next;
        Upload(String id,String to,String name,long size) throws Exception {
            this.id=id; this.to=to; this.name=name; this.size=size;
            part=storage.resolve("files").resolve(id+".part"); done=storage.resolve("files").resolve(id+".bin");
            digest=MessageDigest.getInstance("SHA-256"); out=new BufferedOutputStream(Files.newOutputStream(part,StandardOpenOption.CREATE_NEW));
        }
        public void close() throws IOException { out.close(); Files.deleteIfExists(part); }
    }
    private final class Session {
        final Socket socket; final String id=UUID.randomUUID().toString();
        final AtomicBoolean finishing=new AtomicBoolean();
        final Map<String,Upload> uploads=new HashMap<>();
        final Set<String> downloads=ConcurrentHashMap.newKeySet();
        volatile Channel channel; String user; SecretKey key; String keyId;
        Session(Socket socket) { this.socket=socket; }
        void run() {
            boolean registered=false;
            try(socket) {
                socket.setSoTimeout(15000); socket.setTcpNoDelay(true);
                var in=new DataInputStream(new BufferedInputStream(socket.getInputStream()));
                var out=new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
                Wire.plain(out,new Packet("HELLO",id,"","","AESCHAT/2",RSAUtil.fingerprint(rsa.getPublic()),Wire.VERSION,0,RSAUtil.encodePublic(rsa.getPublic())));
                Packet join=Wire.plain(in);
                if(!join.type().equals("JOIN") || join.size()!=Wire.VERSION || !join.text().matches("[\\p{L}\\p{N}_-]{1,24}")) throw new IOException("JOIN/version/username không hợp lệ");
                user=join.text(); key=RSAUtil.moKhoaAES(join.data(),rsa.getPrivate()); keyId=UUID.randomUUID().toString();
                channel=new Channel(socket,in,out,key,id,true);
                synchronized(online) {
                    if(online.containsKey(user)) {
                        // Synchronous rejection before normal frame writer has work.
                        Wire.bytes(out, rejection(key,id)); out.flush(); return;
                    }
                    repository.join(id,user,socket.getRemoteSocketAddress().toString());
                    online.put(user,this); registered=true; historyKeys.put(keyId,key);
                    channel.send(Packet.of("JOINED",id,"",user));
                }
                socket.setSoTimeout(120000); log("JOIN "+user+" session="+id); broadcastUsers();
                while(!finishing.get()) {
                    Packet p=channel.read();
                    try { handle(p); }
                    catch(java.sql.SQLException e) { log("DB ERROR "+e.getMessage()); offer(Packet.of("ERROR",p.id(),"","CSDL lỗi; thao tác chưa được xác nhận")); }
                    catch(IllegalArgumentException e) { offer(Packet.of("ERROR",p.id(),"",e.getMessage())); }
                }
            } catch(Exception e) { if(!closed) log("DISCONNECT "+user+": "+e.getClass().getSimpleName()+" "+e.getMessage()); }
            finally {
                finishing.set(true); if(channel!=null) channel.close();
                for(Upload u:uploads.values()) {
                    try { u.close(); repository.status(u.id,null,"INTERRUPTED"); } catch(Exception e) { log("Cleanup: "+e.getMessage()); }
                }
                if(registered) {
                    online.remove(user,this);
                    try { repository.leave(id); } catch(Exception e) { log("DB leave: "+e.getMessage()); }
                    broadcastUsers(); log("OFFLINE "+user);
                }
            }
        }
        byte[] rejection(SecretKey key,String id) throws Exception {
            byte[] sealed=Crypto.seal(key,Wire.encode(Packet.of("ERROR","","","Username đang online")),"AESCHAT/2/"+id+"/S/0");
            var b=new ByteArrayOutputStream(); var d=new DataOutputStream(b); d.writeLong(0); d.write(sealed); return b.toByteArray();
        }
        void offer(Packet p) { try { channel.send(p); } catch(Exception e) { channel.close(); } }
        Session recipient(String name) {
            Session other=online.get(name);
            if(other==null || other==this || other.finishing.get()) throw new IllegalArgumentException("Người nhận offline hoặc không hợp lệ: "+name);
            return other;
        }
        void handle(Packet p) throws Exception {
            switch(p.type()) {
                case "PING" -> offer(Packet.of("PONG",p.id(),"",""));
                case "BYE" -> finishing.set(true);
                case "ROTATE" -> {
                    SecretKey next=RSAUtil.moKhoaAES(p.data(),rsa.getPrivate());
                    key=next; keyId=UUID.randomUUID().toString(); historyKeys.put(keyId,key);
                    channel.serverRotate(key,Packet.of("ROTATED",p.id(),"","Đã đổi khóa AES-GCM"));
                }
                case "TEXT", "LAB" -> text(p);
                case "HISTORY" -> history(p);
                case "CONTACTS" -> offer(Packet.of("CONTACTS",p.id(),"",String.join("\n",repository.users(user))));
                case "FILE_BEGIN" -> begin(p);
                case "FILE_CHUNK" -> chunk(p);
                case "FILE_END" -> end(p);
                case "FILE_CANCEL" -> cancel(p.id());
                case "FILE_ACK" -> ack(p);
                case "FILE_GET" -> {
                    var f=repository.fileFor(p.id(),user);
                    if(f==null || f.hash()==null || !Files.isRegularFile(Path.of(f.path()))) throw new IllegalArgumentException("Không có quyền hoặc file không tồn tại");
                    deliver(f,this,null);
                }
                default -> throw new IOException("Loại gói không hỗ trợ: "+p.type());
            }
        }
        void text(Packet p) throws Exception {
            boolean lab=p.type().equals("LAB"); Session other=lab?null:recipient(p.to());
            if(p.text().isBlank() || p.text().getBytes(StandardCharsets.UTF_8).length>8192) throw new IllegalArgumentException("Văn bản rỗng hoặc vượt 8192 byte UTF-8");
            String messageId=UUID.randomUUID().toString(); String stats=KetQuaDemChuCai.dem(p.text());
            byte[] stored=Crypto.seal(key,p.text().getBytes(StandardCharsets.UTF_8),"history/"+messageId);
            repository.message(messageId,user,lab?null:other.user,p.type(),keyId,stored,stats);
            String time=ChatRepository.now();
            offer(Packet.event("RESULT",p.id(),user,p.to(),p.text(),stats));
            if(!lab) {
                Packet routed=Packet.event("CHAT",messageId,user,other.user,p.text(),time);
                try { other.channel.send(routed); offer(routed); }
                catch(IOException e) { offer(Packet.of("ERROR",p.id(),other.user,"Người nhận vừa mất kết nối; chưa xác nhận giao tin")); }
            }
        }
        void history(Packet p) throws Exception {
            for(var e:repository.history(user,p.to())) {
                if(e.kind().equals("FILE")) {
                    offer(new Packet("HISTORY_FILE",e.id(),e.sender(),e.recipient(),e.name(),e.time()+"|"+e.status()+"|"+Objects.toString(e.hash(),""),e.size(),0,null));
                } else {
                    SecretKey old=historyKeys.get(e.keyId()); String text="[Bản mã lịch sử: khóa phiên không còn trong RAM]";
                    if(old!=null) text=new String(Crypto.open(old,e.cipher(),"history/"+e.id()),StandardCharsets.UTF_8);
                    offer(new Packet("HISTORY_ITEM",e.id(),e.sender(),e.recipient(),text,e.time(),0,0,e.stats().getBytes(StandardCharsets.UTF_8)));
                }
            }
            offer(Packet.of("HISTORY_END",p.id(),p.to(),""));
        }
        void begin(Packet p) throws Exception {
            Session other=recipient(p.to());
            if(uploads.size()>=2 || p.size()<0 || p.size()>Wire.MAX_FILE) throw new IllegalArgumentException("Giới hạn 2 upload và 1 GiB/file");
            String fileId=UUID.randomUUID().toString();
            String name=p.text().replaceAll("[^\\p{L}\\p{N}._ -]","_");
            if(name.isBlank() || name.length()>180) throw new IllegalArgumentException("Tên file không hợp lệ");
            Upload u=new Upload(fileId,other.user,name,p.size());
            try { repository.file(fileId,user,other.user,name,p.size(),u.done.toString()); }
            catch(Exception e) { u.close(); throw e; }
            uploads.put(fileId,u);
            offer(new Packet("FILE_READY",p.id(),user,other.user,fileId,name,p.size(),0,null));
        }
        void chunk(Packet p) throws Exception {
            Upload u=uploads.get(p.id());
            if(u==null) throw new IOException("Upload ID không thuộc phiên");
            if(p.index()!=u.next || p.data().length==0 || p.data().length>Wire.CHUNK || u.bytes+p.data().length>u.size) throw new IOException("Chunk sai thứ tự/kích thước");
            u.out.write(p.data()); u.digest.update(p.data()); u.bytes+=p.data().length; u.next++;
        }
        void end(Packet p) throws Exception {
            Upload u=uploads.remove(p.id()); if(u==null) throw new IOException("Không có upload");
            try {
                u.out.close(); String hash=AESUtil.hex(u.digest.digest());
                if(u.bytes!=u.size || !hash.equals(p.text())) throw new IOException("SHA-256/kích thước upload không khớp");
                Files.move(u.part,u.done,StandardCopyOption.ATOMIC_MOVE);
                repository.status(u.id,hash,"STORED");
                offer(Packet.of("FILE_STATUS",u.id,u.to,"Server đã nhận đủ; SHA-256 khớp"));
                Session other=online.get(u.to);
                if(other==null) { repository.status(u.id,null,"RECIPIENT_OFFLINE"); offer(Packet.of("FILE_STATUS",u.id,u.to,"Người nhận offline; có thể tải lại từ lịch sử")); }
                else deliver(repository.fileFor(u.id,user),other,this);
            } catch(Exception e) { Files.deleteIfExists(u.part); repository.status(u.id,null,"FAILED"); offer(Packet.of("ERROR",u.id,u.to,e.getMessage())); }
        }
        void cancel(String fileId) throws Exception {
            Upload u=uploads.remove(fileId);
            if(u!=null) { u.close(); repository.status(fileId,null,"CANCELLED"); offer(Packet.of("FILE_STATUS",fileId,u.to,"Đã hủy upload")); }
        }
        void ack(Packet p) throws Exception {
            var f=repository.fileFor(p.id(),user);
            if(f==null || f.hash()==null || !downloads.remove(p.id())) throw new IllegalArgumentException("ACK không thuộc lượt tải của phiên");
            boolean ok=p.text().equals(f.hash());
            // Sender's historical download must not change recipient delivery state.
            if(user.equals(f.recipient())) repository.status(f.id(),null,ok?"DELIVERED":"FAILED");
            String status=ok?"Đã nhận file, SHA-256 khớp":"Nhận file thất bại, SHA-256 không khớp";
            offer(Packet.of("FILE_STATUS",f.id(),f.sender(),status));
            Session sender=online.get(f.sender()); if(sender!=null && sender!=this) sender.offer(Packet.of("FILE_STATUS",f.id(),user,status));
        }
    }
    private void deliver(ChatRepository.Entry f,Session target,Session sender) throws Exception {
        synchronized(target.downloads) {
            if(target.downloads.size()>=4 || !target.downloads.add(f.id())) throw new IllegalArgumentException("File đang được tải hoặc đã đạt 4 lượt tải");
        }
        workers.submit(()-> {
            try {
                target.channel.send(new Packet("FILE_START",f.id(),f.sender(),f.recipient(),f.name(),f.hash(),f.size(),0,null));
                try(var input=new BufferedInputStream(Files.newInputStream(Path.of(f.path())))) {
                    byte[] buffer=new byte[Wire.CHUNK]; int n,index=0;
                    while((n=input.read(buffer))!=-1) target.channel.send(new Packet("FILE_DATA",f.id(),f.sender(),f.recipient(),"","",0,index++,Arrays.copyOf(buffer,n)));
                }
                target.channel.send(Packet.of("FILE_FINISH",f.id(),"",f.hash()));
            } catch(Exception e) {
                target.downloads.remove(f.id());
                target.offer(Packet.of("FILE_ABORT",f.id(),"","Chuyển file bị gián đoạn"));
                if(sender!=null) sender.offer(Packet.of("ERROR",f.id(),target.user,"Không chuyển được file: "+e.getMessage()));
                try { repository.status(f.id(),null,"INTERRUPTED"); } catch(Exception ignored) {}
            }
        });
    }
    @Override public void close() {
        if(!shutdown.compareAndSet(false,true)) return;
        closed=true; try { listener.close(); } catch(IOException ignored) {}
        for(Socket s:connections) try { s.close(); } catch(IOException ignored) {}
        workers.shutdown();
        try { if(!workers.awaitTermination(5,TimeUnit.SECONDS)) workers.shutdownNow(); repository.close(); }
        catch(Exception e) { log("Shutdown: "+e.getMessage()); }
        historyKeys.clear();
    }
}
