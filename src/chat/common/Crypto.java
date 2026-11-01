package chat.common;

import common.AESUtil;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;

/** v2 authenticated records; legacy AESUtil CBC API remains unchanged. */
public final class Crypto {
    private static final SecureRandom RANDOM = new SecureRandom();
    private Crypto() {}
    public static SecretKey randomKey() { byte[] b=new byte[32]; RANDOM.nextBytes(b); return AESUtil.khoaTuBytes(b); }
    public static byte[] seal(SecretKey key, byte[] plain, String context) throws GeneralSecurityException {
        byte[] nonce = new byte[12]; RANDOM.nextBytes(nonce);
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.ENCRYPT_MODE,key,new GCMParameterSpec(128,nonce));
        c.updateAAD(context.getBytes(StandardCharsets.UTF_8)); byte[] encrypted=c.doFinal(plain);
        byte[] result=Arrays.copyOf(nonce,nonce.length+encrypted.length);
        System.arraycopy(encrypted,0,result,12,encrypted.length); return result;
    }
    public static byte[] open(SecretKey key, byte[] encrypted, String context) throws GeneralSecurityException {
        if(encrypted.length<28) throw new GeneralSecurityException("GCM record quá ngắn");
        Cipher c=Cipher.getInstance("AES/GCM/NoPadding");
        c.init(Cipher.DECRYPT_MODE,key,new GCMParameterSpec(128,encrypted,0,12));
        c.updateAAD(context.getBytes(StandardCharsets.UTF_8)); return c.doFinal(encrypted,12,encrypted.length-12);
    }
}
