package chat.common;

import java.io.*;
import java.nio.charset.StandardCharsets;

/** No Java deserialization. Every allocation has a checked upper bound. */
public final class Wire {
    public static final int VERSION = 2, MAX_FRAME = 128 * 1024, CHUNK = 64 * 1024;
    public static final long MAX_FILE = 1024L * 1024 * 1024;
    private Wire() {}
    public static void string(DataOutputStream out, String value) throws IOException {
        byte[] b = value.getBytes(StandardCharsets.UTF_8);
        if (b.length > 32768) throw new IOException("Chuỗi vượt 32768 byte UTF-8");
        bytes(out, b);
    }
    public static String string(DataInputStream in) throws IOException {
        return new String(bytes(in, 32768), StandardCharsets.UTF_8);
    }
    public static void bytes(DataOutputStream out, byte[] b) throws IOException {
        out.writeInt(b.length); out.write(b);
    }
    public static byte[] bytes(DataInputStream in, int max) throws IOException {
        int n = in.readInt();
        if (n < 0 || n > max) throw new IOException("Độ dài không hợp lệ: " + n);
        byte[] b = new byte[n]; in.readFully(b); return b;
    }
    public static byte[] encode(Packet p) throws IOException {
        var buffer = new ByteArrayOutputStream(); var out = new DataOutputStream(buffer);
        for (String s : new String[]{p.type(),p.id(),p.from(),p.to(),p.text(),p.extra()}) string(out,s);
        out.writeLong(p.size()); out.writeInt(p.index()); bytes(out,p.data());
        byte[] b = buffer.toByteArray();
        if (b.length > MAX_FRAME - 64) throw new IOException("Frame quá lớn");
        return b;
    }
    public static Packet decode(byte[] bytes) throws IOException {
        var in = new DataInputStream(new ByteArrayInputStream(bytes));
        Packet p = new Packet(string(in),string(in),string(in),string(in),string(in),string(in),in.readLong(),in.readInt(),bytes(in,CHUNK));
        if (in.available() != 0) throw new IOException("Frame thừa dữ liệu");
        return p;
    }
    public static void plain(DataOutputStream out, Packet p) throws IOException { bytes(out,encode(p)); out.flush(); }
    public static Packet plain(DataInputStream in) throws IOException { return decode(bytes(in,MAX_FRAME)); }
}
