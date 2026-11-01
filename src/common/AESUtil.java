package common;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.function.LongConsumer;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Mã hóa / giải mã AES (AES/CBC/PKCS5Padding, khóa 256 bit).
 *
 * Ba quyết định thiết kế:
 *
 * 1. Khóa người dùng gõ là chuỗi bất kỳ, AES lại chỉ nhận đúng 16/24/32 byte.
 *    Dẫn xuất bằng SHA-256 cho ra 32 byte cố định, nên "abc" hay một câu dài
 *    đều dùng được và cùng chuỗi luôn cho cùng khóa ở cả hai phía.
 *
 * 2. CBC cần IV. IV sinh ngẫu nhiên cho MỖI lần mã hóa và gửi kèm bản mã
 *    (IV không bí mật, chỉ cần không lặp). Nhờ vậy gửi cùng một câu hai lần
 *    sẽ ra hai bản mã khác nhau, kẻ nghe lén không nhận ra tin nhắn trùng.
 *
 * 3. PKCS5 luôn thêm ít nhất 1 byte đệm, nên kích thước bản mã tính trước
 *    được từ kích thước bản rõ: (n / 16 + 1) * 16. Server dùng công thức này
 *    để biết phải đọc bao nhiêu byte cho một file mà không cần dấu kết thúc.
 */
public final class AESUtil {

    private static final SecureRandom RNG = new SecureRandom();

    private AESUtil() {
    }

    /** SHA-256(khoa) -> 32 byte -> SecretKey AES-256. */
    public static SecretKey taoKhoa(String khoaNguoiDung) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(khoaNguoiDung.getBytes(StandardCharsets.UTF_8));
            return new SecretKeySpec(digest, 0, Protocol.AES_KEY_BYTES, Protocol.AES_ALGORITHM);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JDK không có SHA-256", e);
        }
    }

    /** Dựng lại SecretKey từ 32 byte thô (phía Server sau khi mở RSA). */
    public static SecretKey khoaTuBytes(byte[] raw) {
        if (raw.length != Protocol.AES_KEY_BYTES) {
            throw new IllegalArgumentException("Khóa AES phải đúng " + Protocol.AES_KEY_BYTES + " byte");
        }
        return new SecretKeySpec(raw, Protocol.AES_ALGORITHM);
    }

    public static byte[] taoIV() {
        byte[] iv = new byte[Protocol.AES_BLOCK];
        RNG.nextBytes(iv);
        return iv;
    }

    public static long kichThuocBanMa(long kichThuocBanRo) {
        return (kichThuocBanRo / Protocol.AES_BLOCK + 1) * Protocol.AES_BLOCK;
    }

    private static Cipher cipher(int mode, SecretKey key, byte[] iv) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance(Protocol.AES_TRANSFORM);
        c.init(mode, key, new IvParameterSpec(iv));
        return c;
    }

    // ---- Mảng byte (văn bản) ----

    public static byte[] maHoa(byte[] banRo, SecretKey key, byte[] iv) throws GeneralSecurityException {
        return cipher(Cipher.ENCRYPT_MODE, key, iv).doFinal(banRo);
    }

    public static byte[] giaiMa(byte[] banMa, SecretKey key, byte[] iv) throws GeneralSecurityException {
        return cipher(Cipher.DECRYPT_MODE, key, iv).doFinal(banMa);
    }

    public static byte[] maHoaChuoi(String banRo, SecretKey key, byte[] iv) throws GeneralSecurityException {
        return maHoa(banRo.getBytes(StandardCharsets.UTF_8), key, iv);
    }

    public static String giaiMaChuoi(byte[] banMa, SecretKey key, byte[] iv) throws GeneralSecurityException {
        return new String(giaiMa(banMa, key, iv), StandardCharsets.UTF_8);
    }

    // ---- Luồng (file) ----

    /**
     * Đọc đúng {@code kichThuoc} byte bản rõ từ {@code in}, mã hóa theo chunk,
     * ghi bản mã ra {@code out}. Tổng byte ghi ra = kichThuocBanMa(kichThuoc).
     * {@code tienDo} nhận số byte bản rõ đã xử lý (có thể null).
     */
    public static void maHoaLuong(InputStream in, OutputStream out, SecretKey key, byte[] iv,
            long kichThuoc, LongConsumer tienDo) throws IOException, GeneralSecurityException {
        Cipher c = cipher(Cipher.ENCRYPT_MODE, key, iv);
        chayLuong(c, in, out, kichThuoc, tienDo);
    }

    /**
     * Đọc đúng {@code kichThuocBanMa} byte bản mã từ {@code in}, giải mã theo
     * chunk, ghi bản rõ ra {@code out}. Đọc đúng số byte rồi dừng để socket
     * còn dùng tiếp cho gói sau (không dùng CipherInputStream vì nó đọc tới EOF).
     */
    public static void giaiMaLuong(InputStream in, OutputStream out, SecretKey key, byte[] iv,
            long kichThuocBanMa, LongConsumer tienDo) throws IOException, GeneralSecurityException {
        Cipher c = cipher(Cipher.DECRYPT_MODE, key, iv);
        chayLuong(c, in, out, kichThuocBanMa, tienDo);
    }

    private static void chayLuong(Cipher c, InputStream in, OutputStream out, long tong,
            LongConsumer tienDo) throws IOException, GeneralSecurityException {
        byte[] buf = new byte[Protocol.CHUNK];
        long conLai = tong;
        long daXuLy = 0;
        while (conLai > 0) {
            int n = in.read(buf, 0, (int) Math.min(buf.length, conLai));
            if (n < 0) {
                throw new IOException("Luồng kết thúc sớm, còn thiếu " + conLai + " byte");
            }
            byte[] ra = c.update(buf, 0, n);
            if (ra != null) {
                out.write(ra);
            }
            conLai -= n;
            daXuLy += n;
            if (tienDo != null) {
                tienDo.accept(daXuLy);
            }
        }
        byte[] cuoi = c.doFinal();
        if (cuoi != null) {
            out.write(cuoi);
        }
        out.flush();
    }

    // ---- Tiện ích hiển thị ----

    public static String hex(byte[] data) {
        StringBuilder sb = new StringBuilder(data.length * 2);
        for (byte b : data) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String sha256Hex(byte[] data) {
        try {
            return hex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
