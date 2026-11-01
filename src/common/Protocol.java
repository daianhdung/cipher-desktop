package common;

/**
 * Hằng số giao thức dùng chung cho Client và Server.
 *
 * Mỗi gói tin trên TCP = 1 byte loại + payload. Payload ghi bằng
 * DataOutputStream: chuỗi dùng writeUTF, mảng byte dùng int độ dài + bytes
 * (xem {@link GoiTin}). Vì TCP là luồng byte không có ranh giới, độ dài
 * đi trước là cách duy nhất để bên nhận biết gói kết thúc ở đâu.
 *
 * Đặc tả đầy đủ: docs/DoAn_PROTOCOL.md
 */
public final class Protocol {

    private Protocol() {
    }

    public static final int PORT_MAC_DINH = 5555;

    // ---- Client -> Server ----
    /** iv(16) + banMa. Server giải mã, đếm chữ cái, trả RESULT. */
    public static final byte TEXT = 0x01;
    /** tenFile, kichThuocGoc, iv(16), kichThuocBanMa, rồi đúng kichThuocBanMa byte. */
    public static final byte FILE = 0x02;
    /** Client chủ động đóng phiên. */
    public static final byte BYE = 0x03;
    /** Khóa AES đã bọc bằng RSA public key của Server. */
    public static final byte KEY_EXCHANGE = 0x11;

    // ---- Server -> Client ----
    /** RSA public key (X.509) — gói đầu tiên Server gửi ngay khi accept. */
    public static final byte HELLO = 0x10;
    /** Server đã mở được khóa AES, sẵn sàng nhận TEXT/FILE. */
    public static final byte KEY_OK = 0x12;
    /** banRo + demChuCai ("a=3;b=1;..."). */
    public static final byte RESULT = (byte) 0x81;
    /** Server đã lưu file, kèm SHA-256 để Client đối chiếu. */
    public static final byte FILE_OK = (byte) 0x82;
    /** Thông báo lỗi dạng chuỗi. */
    public static final byte ERROR = (byte) 0xFF;

    // ---- Mật mã ----
    public static final String AES_ALGORITHM = "AES";
    public static final String AES_TRANSFORM = "AES/CBC/PKCS5Padding";
    public static final int AES_BLOCK = 16;
    public static final int AES_KEY_BYTES = 32;          // AES-256
    public static final String RSA_ALGORITHM = "RSA";
    public static final String RSA_TRANSFORM = "RSA/ECB/OAEPWithSHA-256AndMGF1Padding";
    public static final int RSA_BITS = 2048;

    // ---- Giới hạn ----
    /** Kích thước tối đa của một mảng byte trong gói (chống OOM khi header lỗi). */
    public static final int MAX_BYTES = 16 * 1024 * 1024;
    /** Chunk đọc/ghi khi stream file. */
    public static final int CHUNK = 64 * 1024;

    public static String tenGoi(byte loai) {
        switch (loai) {
            case TEXT:         return "TEXT";
            case FILE:         return "FILE";
            case BYE:          return "BYE";
            case KEY_EXCHANGE: return "KEY_EXCHANGE";
            case HELLO:        return "HELLO";
            case KEY_OK:       return "KEY_OK";
            case RESULT:       return "RESULT";
            case FILE_OK:      return "FILE_OK";
            case ERROR:        return "ERROR";
            default:           return String.format("0x%02X", loai);
        }
    }
}
