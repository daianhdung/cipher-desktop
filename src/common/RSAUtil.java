package common;

import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.X509EncodedKeySpec;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;

/**
 * Trao đổi khóa AES bằng RSA (RSA-OAEP, 2048 bit).
 *
 * AES là mã đối xứng: hai bên phải có cùng khóa, nhưng gửi khóa thô qua mạng
 * thì ai bắt gói cũng đọc được. Server sinh cặp khóa RSA, gửi public key
 * (không bí mật) cho Client; Client bọc 32 byte khóa AES bằng public key đó
 * và gửi lại. Chỉ Server có private key mới mở được. Đây là mô hình
 * hybrid encryption cho demo Client–Server; không xác thực danh tính Server.
 *
 * OAEP thay cho PKCS#1 v1.5 vì padding cũ đã có tấn công lựa chọn bản mã.
 */
public final class RSAUtil {

    private RSAUtil() {
    }

    public static KeyPair taoCapKhoa() {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance(Protocol.RSA_ALGORITHM);
            g.initialize(Protocol.RSA_BITS);
            return g.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("JDK không có RSA", e);
        }
    }

    /** X.509 SubjectPublicKeyInfo — định dạng chuẩn để gửi qua mạng. */
    public static byte[] encodePublic(PublicKey pub) {
        return pub.getEncoded();
    }

    public static PublicKey decodePublic(byte[] encoded) throws GeneralSecurityException {
        return KeyFactory.getInstance(Protocol.RSA_ALGORITHM)
                .generatePublic(new X509EncodedKeySpec(encoded));
    }

    public static byte[] bocKhoaAES(SecretKey aes, PublicKey pub) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance(Protocol.RSA_TRANSFORM);
        c.init(Cipher.ENCRYPT_MODE, pub);
        return c.doFinal(aes.getEncoded());
    }

    public static SecretKey moKhoaAES(byte[] boc, PrivateKey priv) throws GeneralSecurityException {
        Cipher c = Cipher.getInstance(Protocol.RSA_TRANSFORM);
        c.init(Cipher.DECRYPT_MODE, priv);
        return AESUtil.khoaTuBytes(c.doFinal(boc));
    }

    /** SHA-256 của public key, rút gọn — để hiển thị trên GUI/console đối chiếu. */
    public static String fingerprint(PublicKey pub) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(pub.getEncoded());
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) {
                if (i > 0) {
                    sb.append(':');
                }
                sb.append(String.format("%02X", d[i]));
            }
            return sb.toString();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
