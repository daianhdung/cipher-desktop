package common;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.KeyPair;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.Map;
import java.util.Random;
import javax.crypto.SecretKey;

/**
 * Tự kiểm tra package common bằng main() — không cần JUnit.
 * Chạy: java -cp build/classes common.CommonSelfTest
 * Mọi dòng phải in OK; có FAIL thì exit code 1.
 */
public class CommonSelfTest {

    private static int loi = 0;

    public static void main(String[] args) throws Exception {
        SecretKey k = AESUtil.taoKhoa("khoa bi mat 123");

        // 1. Dẫn xuất khóa: cùng chuỗi -> cùng khóa, khác chuỗi -> khác khóa, đúng 32 byte
        check("khóa 32 byte", k.getEncoded().length == 32);
        check("cùng chuỗi -> cùng khóa",
                Arrays.equals(k.getEncoded(), AESUtil.taoKhoa("khoa bi mat 123").getEncoded()));
        check("khác chuỗi -> khác khóa",
                !Arrays.equals(k.getEncoded(), AESUtil.taoKhoa("khoa bi mat 124").getEncoded()));

        // 2. Roundtrip văn bản, có tiếng Việt
        String banRo = "Xin chào AES 123 — Đại Anh Dũng";
        byte[] iv = AESUtil.taoIV();
        byte[] banMa = AESUtil.maHoaChuoi(banRo, k, iv);
        check("giải mã chuỗi khớp", banRo.equals(AESUtil.giaiMaChuoi(banMa, k, iv)));
        check("bản mã dài đúng công thức",
                banMa.length == AESUtil.kichThuocBanMa(banRo.getBytes("UTF-8").length));
        byte[] banMa2 = AESUtil.maHoaChuoi(banRo, k, AESUtil.taoIV());
        check("IV khác -> bản mã khác", !Arrays.equals(banMa, banMa2));
        boolean saiKhoa;
        try {
            AESUtil.giaiMaChuoi(banMa, AESUtil.taoKhoa("sai"), iv);
            saiKhoa = false;
        } catch (Exception e) {
            saiKhoa = true;
        }
        check("sai khóa -> ném lỗi padding", saiKhoa);

        // 3. Kích thước bản mã cho các biên
        for (long n : new long[]{0, 1, 15, 16, 17, 31, 32, 1_000_000}) {
            byte[] du = new byte[(int) n];
            new Random(n).nextBytes(du);
            byte[] ra = AESUtil.maHoa(du, k, iv);
            check("kichThuocBanMa(" + n + ") = " + ra.length, ra.length == AESUtil.kichThuocBanMa(n));
        }

        // 4. Roundtrip luồng (file) qua nhiều chunk, so SHA-256
        byte[] file = new byte[3 * Protocol.CHUNK + 1234];
        new Random(42).nextBytes(file);
        ByteArrayOutputStream maHoa = new ByteArrayOutputStream();
        long[] cuoi = {0};
        AESUtil.maHoaLuong(new ByteArrayInputStream(file), maHoa, k, iv, file.length, v -> cuoi[0] = v);
        check("tiến độ mã hóa chạy tới hết", cuoi[0] == file.length);
        check("bản mã luồng dài đúng", maHoa.size() == AESUtil.kichThuocBanMa(file.length));
        ByteArrayOutputStream giai = new ByteArrayOutputStream();
        AESUtil.giaiMaLuong(new ByteArrayInputStream(maHoa.toByteArray()), giai, k, iv, maHoa.size(), null);
        check("SHA-256 file sau giải mã khớp",
                AESUtil.sha256Hex(file).equals(AESUtil.sha256Hex(giai.toByteArray())));
        // luồng giải mã chỉ ăn đúng số byte, phần đuôi còn nguyên
        byte[] duoi = "DUOI".getBytes();
        ByteArrayOutputStream ghep = new ByteArrayOutputStream();
        ghep.write(maHoa.toByteArray());
        ghep.write(duoi);
        ByteArrayInputStream inGhep = new ByteArrayInputStream(ghep.toByteArray());
        AESUtil.giaiMaLuong(inGhep, new ByteArrayOutputStream(), k, iv, maHoa.size(), null);
        check("giải mã luồng không ăn lố byte của gói sau", inGhep.available() == duoi.length);

        // 5. RSA bọc/mở khóa AES
        KeyPair cap = RSAUtil.taoCapKhoa();
        PublicKey pubDaGui = RSAUtil.decodePublic(RSAUtil.encodePublic(cap.getPublic()));
        byte[] boc = RSAUtil.bocKhoaAES(k, pubDaGui);
        check("khóa bọc RSA-2048 dài 256 byte", boc.length == 256);
        check("khóa bọc không lộ khóa thô", !chua(boc, k.getEncoded()));
        SecretKey mo = RSAUtil.moKhoaAES(boc, cap.getPrivate());
        check("Server mở lại đúng khóa AES", Arrays.equals(k.getEncoded(), mo.getEncoded()));
        check("fingerprint có dạng XX:XX", RSAUtil.fingerprint(cap.getPublic()).matches("([0-9A-F]{2}:){7}[0-9A-F]{2}"));

        // 6. Đếm chữ cái
        String dem = LetterFrequency.dem("Xin chao AES 123");
        check("đếm 'Xin chao AES 123' -> " + dem, dem.equals("a=2;c=1;e=1;h=1;i=1;n=1;o=1;s=1;x=1"));
        Map<Character, Integer> p = LetterFrequency.parse(dem);
        check("parse giữ thứ tự, a đầu tiên", p.keySet().iterator().next() == 'a' && p.get('a') == 2);
        check("tổng chữ cái = 10", LetterFrequency.tongChuCai(p) == 10);
        check("chữ Việt có dấu là chữ cái", LetterFrequency.dem("Đà").equals("à=1;đ=1"));
        check("chuỗi rỗng -> rỗng", LetterFrequency.dem("123 !?").isEmpty() && LetterFrequency.parse("").isEmpty());

        System.out.println();
        System.out.println(loi == 0 ? "TẤT CẢ OK" : loi + " kiểm tra FAIL");
        System.exit(loi == 0 ? 0 : 1);
    }

    private static boolean chua(byte[] lon, byte[] nho) {
        outer:
        for (int i = 0; i + nho.length <= lon.length; i++) {
            for (int j = 0; j < nho.length; j++) {
                if (lon[i + j] != nho[j]) {
                    continue outer;
                }
            }
            return true;
        }
        return false;
    }

    private static void check(String ten, boolean ok) {
        System.out.printf("[%s] %s%n", ok ? " OK " : "FAIL", ten);
        if (!ok) {
            loi++;
        }
    }
}
