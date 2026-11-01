package chat.common;

/** Immutable bounded v2 message. Field meanings are documented in docs/PROTOCOL.md. */
public record Packet(String type, String id, String from, String to, String text,
                     String extra, long size, int index, byte[] data) {
    public Packet {
        if (data == null) data = new byte[0];
    }
    public static Packet of(String type, String id, String to, String text) {
        return new Packet(type, id, "", to, text, "", 0, 0, null);
    }
    public static Packet event(String type, String id, String from, String to, String text, String extra) {
        return new Packet(type, id, from, to, text, extra, 0, 0, null);
    }
}
