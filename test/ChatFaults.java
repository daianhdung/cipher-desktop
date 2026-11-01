import chat.client.ChatClient;
import chat.common.*;
import chat.server.*;
import common.*;
import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.SecretKey;

/** Adversarial protocol and persistence failures over real sockets. */
public class ChatFaults {
    static final class Raw implements AutoCloseable {
        Socket socket;Channel channel;SecretKey key;String session;DataOutputStream out;
        Raw(int port,String name)throws Exception {
            socket=new Socket("127.0.0.1",port);socket.setSoTimeout(10000);
            var in=new DataInputStream(new BufferedInputStream(socket.getInputStream()));out=new DataOutputStream(socket.getOutputStream());
            Packet hello=Wire.plain(in);session=hello.id();key=Crypto.randomKey();
            Wire.plain(out,new Packet("JOIN","","","",name,"",2,0,RSAUtil.bocKhoaAES(key,RSAUtil.decodePublic(hello.data()))));
            channel=new Channel(socket,in,out,key,session,false);until("JOINED");
        }
        Packet until(String type)throws Exception{for(int i=0;i<30;i++){Packet p=channel.read();if(p.type().equals(type))return p;}throw new AssertionError(type);}
        void send(Packet p)throws Exception{channel.send(p);}
        public void close(){channel.close();}
    }
    public static void main(String[]args)throws Exception {
        Path dir=Files.createTempDirectory("chat-faults-");var server=new ChatServer("127.0.0.1",0,dir);
        Thread.ofVirtual().start(()->{try{server.run();}catch(Exception e){throw new RuntimeException(e);}});
        try(ChatIntegration.Peer alice=new ChatIntegration.Peer(dir.resolve("alice"));ChatIntegration.Peer bob=new ChatIntegration.Peer(dir.resolve("bob"))) {
            int port=server.port();alice.client.connect("127.0.0.1",port,"Alice","");bob.client.connect("127.0.0.1",port,"Bob","");
            try(Raw bad=new Raw(port,"BadMetadata")) {
                bad.send(new Packet("FILE_BEGIN","invalid","","Bob","x.bin","",Wire.MAX_FILE+1,0,null));
                ChatIntegration.check(bad.until("ERROR").text().contains("GiB"),"oversized file metadata rejected without allocation");
                bad.send(new Packet("FILE_BEGIN","valid","","Bob","../../escape.bin","",8,0,null));
                String upload=bad.until("FILE_READY").text();
                bad.send(new Packet("FILE_CHUNK",upload,"","Bob","","",0,7,new byte[4]));
                try {bad.until("FILE_STATUS");throw new AssertionError("wrong chunk accepted");}catch(IOException expected){}
            }
            try(Raw bad=new Raw(port,"Tamper")) {
                byte[] cipher=Crypto.seal(Crypto.randomKey(),Wire.encode(Packet.of("LAB","fake","","secret")),"AESCHAT/2/"+bad.session+"/C/0");
                bad.out.writeInt(8+cipher.length);bad.out.writeLong(0);bad.out.write(cipher);bad.out.flush();
                try{bad.until("RESULT");throw new AssertionError("wrong key accepted");}catch(IOException expected){}
                System.out.println("PASS incorrect GCM session key closes only offending session");
            }
            try(Raw bad=new Raw(port,"Replay")) {
                byte[] cipher=Crypto.seal(bad.key,Wire.encode(Packet.of("LAB","replay","","replay")),"AESCHAT/2/"+bad.session+"/C/0");
                for(int i=0;i<2;i++){bad.out.writeInt(8+cipher.length);bad.out.writeLong(0);bad.out.write(cipher);bad.out.flush();}
                try{while(true)bad.channel.read();}catch(IOException expected){}
                System.out.println("PASS replayed sequence rejected");
            }
            try(Socket partial=new Socket("127.0.0.1",port)) {var out=new DataOutputStream(partial.getOutputStream());out.writeInt(100);out.writeByte(1);out.flush();}
            // A live external transaction forces real JDBC write failure; release then retry.
            try(var lock=DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("chat.db"));var statement=lock.createStatement()) {
                statement.execute("BEGIN IMMEDIATE");
                String request=alice.client.lab("locked database request");
                alice.waitFor(p->p.type().equals("ERROR")&&p.id().equals(request)&&p.text().contains("CSDL"));
                statement.execute("ROLLBACK");
            }
            String good=alice.client.lab("database recovered");alice.waitFor(p->p.type().equals("RESULT")&&p.id().equals(good));
            alice.client.text("Bob","other sessions survived faults");bob.waitFor(p->p.type().equals("CHAT")&&p.text().equals("other sessions survived faults"));
            System.out.println("PASS DB busy failure reported, rollback/recovery, unrelated sessions preserved");
            try(var paths=Files.list(dir.resolve("files"))) {ChatIntegration.check(paths.noneMatch(p->p.toString().endsWith(".part")),"interrupted file removed and safe generated storage paths");}
            try(var db=DriverManager.getConnection("jdbc:sqlite:"+dir.resolve("chat.db"));var s=db.createStatement();var r=s.executeQuery("SELECT ciphertext FROM messages")) {
                while(r.next())ChatIntegration.check(!new String(r.getBytes(1),java.nio.charset.StandardCharsets.UTF_8).contains("database recovered"),"database content is ciphertext");
            }
            server.close();alice.type("DISCONNECTED");bob.type("DISCONNECTED");System.out.println("PASS active clients notified on Server shutdown");
            try(ChatClient unavailable=new ChatClient(dir.resolve("unavailable"),p->{})) {
                try{unavailable.connect("127.0.0.1",port,"Unavailable","");throw new AssertionError("unavailable accepted");}catch(IOException expected){System.out.println("PASS connection refused handled");}
            }
        } finally {
            server.close();try(var paths=Files.walk(dir)){for(Path p:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
        System.out.println("CHAT FAULT TESTS OK");
    }
}
