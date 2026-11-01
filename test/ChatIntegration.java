import chat.client.ChatClient;
import chat.common.*;
import chat.server.*;
import common.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.security.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;

/** Real sockets, real SQLite, bounded streaming, restart; no Swing and no LAN claim. */
public class ChatIntegration {
    static final class Peer implements AutoCloseable {
        final List<Packet> log=new CopyOnWriteArrayList<>();
        final ChatClient client;
        Peer(Path dir) { client=new ChatClient(dir,log::add); }
        Packet waitFor(Predicate<Packet> predicate) throws Exception {
            long until=System.nanoTime()+TimeUnit.SECONDS.toNanos(20);
            while(System.nanoTime()<until) {
                for(Packet p:log) if(predicate.test(p)) return p;
                Thread.sleep(10);
            }
            throw new AssertionError("Timeout; events="+log.stream().map(p->p.type()+":"+p.text()).toList());
        }
        Packet type(String type) throws Exception { return waitFor(p->p.type().equals(type)); }
        public void close() { client.close(); }
    }
    static void check(boolean ok,String what) { if(!ok) throw new AssertionError(what); System.out.println("PASS "+what); }
    static Thread start(ChatServer server) { return Thread.ofVirtual().start(()->{try{server.run();}catch(Exception e){throw new RuntimeException(e);}}); }
    static long count(Path db,String table) throws Exception {
        try(var c=DriverManager.getConnection("jdbc:sqlite:"+db);var s=c.createStatement();var r=s.executeQuery("SELECT COUNT(*) FROM "+table)) {r.next(); return r.getLong(1);}
    }
    public static void main(String[] args) throws Exception {
        Path dir=Files.createTempDirectory("aes-chat-test-");
        try { run(dir); } finally {
            try(var paths=Files.walk(dir)) { for(Path p:paths.sorted(Comparator.reverseOrder()).toList()) Files.deleteIfExists(p); }
        }
    }
    static void run(Path dir) throws Exception {
        var key=Crypto.randomKey(); byte[] encrypted=Crypto.seal(key,"Đại Anh Dũng".getBytes(java.nio.charset.StandardCharsets.UTF_8),"test");
        check(new String(Crypto.open(key,encrypted,"test"),java.nio.charset.StandardCharsets.UTF_8).equals("Đại Anh Dũng"),"GCM Vietnamese roundtrip");
        encrypted[15]^=1;
        try { Crypto.open(key,encrypted,"test"); throw new AssertionError("tamper accepted"); } catch(GeneralSecurityException expected) { System.out.println("PASS tampered tag rejected"); }
        try { Wire.bytes(new DataInputStream(new ByteArrayInputStream(new byte[]{127,-1,-1,-1})),Wire.MAX_FRAME); throw new AssertionError("allocation"); } catch(IOException expected) { System.out.println("PASS oversized frame rejected"); }
        Packet framed=Packet.of("LAB","partial","","Tiếng Việt");
        var frameBuffer=new java.io.ByteArrayOutputStream(); Wire.plain(new java.io.DataOutputStream(frameBuffer),framed);
        var fragmented=new java.io.FilterInputStream(new java.io.ByteArrayInputStream(frameBuffer.toByteArray())) {
            @Override public int read(byte[] b,int off,int len)throws java.io.IOException { return super.read(b,off,Math.min(1,len)); }
        };
        check(Wire.plain(new java.io.DataInputStream(fragmented)).text().equals(framed.text()),"one-byte fragmented reads decoded correctly");
        Path data=dir.resolve("server"); ChatServer server=new ChatServer("127.0.0.1",0,data); start(server); int port=server.port();
        try(Peer alice=new Peer(dir.resolve("alice"));Peer bob=new Peer(dir.resolve("bob"));Peer charlie=new Peer(dir.resolve("charlie"))) {
            alice.client.connect("localhost",port,"Alice",""); bob.client.connect("localhost",port,"Bob",""); charlie.client.connect("localhost",port,"Charlie","");
            check(alice.waitFor(p->p.type().equals("USERS")&&p.text().contains("Bob")&&p.text().contains("Charlie"))!=null,"online list contains three independent sessions");
            try(Peer duplicate=new Peer(dir.resolve("duplicate"))) {
                try { duplicate.client.connect("localhost",port,"Alice",""); throw new AssertionError("duplicate username"); } catch(IOException expected) { System.out.println("PASS duplicate active username rejected"); }
            }
            String req=alice.client.text("Bob","Xin chào Bob — Đại Anh Dũng");
            Packet result=alice.waitFor(p->p.type().equals("RESULT")&&p.id().equals(req));
            check(result.extra().equals(LetterFrequency.dem(result.text())),"server counts Vietnamese letters");
            Packet message=bob.type("CHAT");
            check(message.from().equals("Alice") && charlie.log.stream().noneMatch(p->p.type().equals("CHAT")),"private routing excludes Charlie");
            bob.client.text("Alice","Hello Alice"); alice.waitFor(p->p.type().equals("CHAT")&&p.from().equals("Bob"));
            try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
                List<Future<?>> jobs=new ArrayList<>();
                for(int i=0;i<20;i++) { final int n=i; jobs.add(pool.submit(()->{ try { (n%2==0?alice:bob).client.text(n%2==0?"Bob":"Alice","concurrent "+n); }catch(Exception e){throw new RuntimeException(e);} })); }
                for(var f:jobs) f.get();
            }
            bob.waitFor(p->p.type().equals("CHAT")&&p.text().equals("concurrent 18")); alice.waitFor(p->p.type().equals("CHAT")&&p.text().equals("concurrent 19"));
            System.out.println("PASS concurrent messages and reply");
            alice.client.rotate("khóa do người dùng nhập");
            alice.client.text("Bob","after rotation"); bob.waitFor(p->p.type().equals("CHAT")&&p.text().equals("after rotation"));
            String lab=alice.client.lab("Đà 123 Aa"); check(alice.waitFor(p->p.type().equals("RESULT")&&p.id().equals(lab)).extra().equals(LetterFrequency.dem("Đà 123 Aa")),"rotation and standalone Lab preserved");
            check(!alice.client.preview("same").equals(alice.client.preview("same")),"fresh GCM nonces");
            Path file=dir.resolve("large.bin");
            try(var out=Files.newOutputStream(file)) { byte[] bytes=new byte[65536];new Random(7).nextBytes(bytes); for(int i=0;i<512;i++) out.write(bytes); }
            AtomicBoolean cancel=new AtomicBoolean();
            try(var pool=Executors.newVirtualThreadPerTaskExecutor()) {
                Future<?> upload=pool.submit(()->{try{alice.client.file("Bob",file,cancel);}catch(Exception e){throw new RuntimeException(e);}});
                charlie.client.text("Bob","unrelated during transfer"); bob.waitFor(p->p.type().equals("CHAT")&&p.text().equals("unrelated during transfer"));
                alice.client.rotate("rotated during upload"); upload.get(30,TimeUnit.SECONDS);
            }
            Packet received=bob.type("FILE_RECEIVED");
            check(Files.mismatch(file,Path.of(received.extra()))==-1,"32 MiB streamed file integrity including rotation during upload");
            alice.waitFor(p->p.type().equals("FILE_STATUS")&&p.text().startsWith("Đã nhận file"));
            check(charlie.log.stream().noneMatch(p->p.type().equals("FILE_RECEIVED")),"file remains private");
            charlie.client.download(received.id()); charlie.waitFor(p->p.type().equals("ERROR")&&p.id().equals(received.id())); System.out.println("PASS unauthorized retrieval rejected");
            Path zero=dir.resolve("empty.bin"); Files.createFile(zero); alice.client.file("Bob",zero,new AtomicBoolean()); bob.waitFor(p->p.type().equals("FILE_RECEIVED")&&p.size()==0);
            System.out.println("PASS zero byte transfer");
            alice.client.history("Bob"); alice.waitFor(p->p.type().equals("HISTORY_ITEM")&&p.text().equals("after rotation")); alice.type("HISTORY_FILE");
            System.out.println("PASS conversation history with content and attachments");
            AtomicBoolean stop=new AtomicBoolean(true); alice.client.file("Bob",file,stop); alice.waitFor(p->p.type().equals("FILE_STATUS")&&p.text().contains("hủy")); System.out.println("PASS cancelled transfer");
            int presenceCursor=bob.log.size();
            charlie.client.close();
            bob.waitFor(p->p.type().equals("USERS")&&!p.text().contains("Charlie")&&bob.log.indexOf(p)>=presenceCursor);
            alice.client.text("Charlie","offline"); alice.waitFor(p->p.type().equals("ERROR")&&p.text().contains("offline"));
            alice.client.text("Bob","still connected"); bob.waitFor(p->p.type().equals("CHAT")&&p.text().equals("still connected"));
            System.out.println("PASS disconnect cleanup, offline failure, other sessions alive");
            // Malformed connections cannot bring down the server.
            try(Socket bad=new Socket("localhost",port)) {var out=new DataOutputStream(bad.getOutputStream());out.writeInt(Integer.MAX_VALUE);out.flush();}
            check(server.onlineCount()==2,"malformed client isolated");
        } finally {server.close();}
        check(count(data.resolve("chat.db"),"messages")>=25 && count(data.resolve("chat.db"),"letter_statistics")>=25,"SQLite records persisted");
        ChatServer restart=new ChatServer("127.0.0.1",0,data);start(restart);
        try(Peer alice=new Peer(dir.resolve("alice-restart"))) {
            alice.client.connect("localhost",restart.port(),"Alice","");alice.client.history("Bob");
            Packet old=alice.waitFor(p->p.type().equals("HISTORY_ITEM")&&p.text().contains("khóa phiên không còn"));
            check(old.data().length>0,"persisted letter statistics remain readable after restart");
            Packet attachment=alice.type("HISTORY_FILE");
            alice.client.download(attachment.id()); alice.type("FILE_RECEIVED");
            System.out.println("PASS restart retains ciphertext metadata and downloadable files; missing keys explicit");
        } finally {restart.close();}
        System.out.println("CHAT INTEGRATION OK");
    }
}
