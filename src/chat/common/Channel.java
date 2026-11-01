package chat.common;

import java.io.*;
import java.net.Socket;
import java.security.GeneralSecurityException;
import java.util.concurrent.*;
import javax.crypto.SecretKey;

/** One bounded writer per connection; network waits never hold a Swing or router lock. */
public final class Channel implements AutoCloseable {
    private record Write(Packet packet, SecretKey newKey) {}
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final String context, sendDirection, receiveDirection;
    private final BlockingQueue<Write> queue = new ArrayBlockingQueue<>(32);
    private volatile SecretKey receiveKey, pendingKey;
    private SecretKey sendKey;
    private long sent, received;
    private volatile boolean closed;
    private final Thread writer;
    public Channel(Socket socket, DataInputStream in, DataOutputStream out, SecretKey key, String session, boolean server) {
        this.socket=socket; this.in=in; this.out=out; sendKey=key; receiveKey=key;
        context="AESCHAT/2/"+session+"/"; sendDirection=server?"S":"C"; receiveDirection=server?"C":"S";
        writer=Thread.ofVirtual().name("chat-writer").start(this::writeLoop);
    }
    public void send(Packet p) throws IOException { Wire.encode(p); enqueue(new Write(p,null)); }
    private void enqueue(Write w) throws IOException {
        if(closed) throw new IOException("Kết nối đã đóng");
        try {
            if(!queue.offer(w,3,TimeUnit.SECONDS)) { close(); throw new IOException("Người nhận quá chậm"); }
        } catch(InterruptedException e) { Thread.currentThread().interrupt(); throw new IOException(e); }
    }
    /** Server queues the write-key transition together with ACK, after older records. */
    public void serverRotate(SecretKey key, Packet ack) throws IOException {
        receiveKey=key; enqueue(new Write(ack,key));
    }
    public void expectKey(SecretKey key) { pendingKey=key; }
    public void clientCommitKey(SecretKey key) throws IOException { enqueue(new Write(null,key)); }
    public Packet read() throws IOException, GeneralSecurityException {
        byte[] frame=Wire.bytes(in,Wire.MAX_FRAME);
        var d=new DataInputStream(new ByteArrayInputStream(frame));
        long seq=d.readLong();
        if(seq!=received) throw new IOException("Sai thứ tự record");
        byte[] cipher=d.readAllBytes(); String aad=context+receiveDirection+"/"+seq;
        byte[] plain;
        try { plain=Crypto.open(receiveKey,cipher,aad); }
        catch(GeneralSecurityException ex) {
            SecretKey next=pendingKey; if(next==null) throw ex;
            plain=Crypto.open(next,cipher,aad); receiveKey=next; pendingKey=null;
        }
        received++; return Wire.decode(plain);
    }
    private void writeLoop() {
        try {
            while(!closed) {
                Write w=queue.take(); if(w.newKey()!=null) sendKey=w.newKey();
                if(w.packet()==null) continue;
                byte[] body=Crypto.seal(sendKey,Wire.encode(w.packet()),context+sendDirection+"/"+sent);
                out.writeInt(8+body.length); out.writeLong(sent++); out.write(body); out.flush();
            }
        } catch(Exception e) { close(); }
    }
    @Override public void close() {
        closed=true; try { socket.close(); } catch(IOException ignored) {}
        if(writer!=null) writer.interrupt(); queue.clear();
    }
}
