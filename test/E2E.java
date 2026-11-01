import client.ClientConnection;
import java.io.File;
import java.util.concurrent.*;

/** Kịch bản E2E không GUI: dùng ClientConnection thật nói chuyện với MockServer thật. */
public class E2E {
    static BlockingQueue<String> q = new LinkedBlockingQueue<>();
    static String cho(String prefix) throws Exception {
        String s = q.poll(10, TimeUnit.SECONDS);
        if (s == null || !s.startsWith(prefix)) throw new AssertionError("mong " + prefix + " nhưng nhận " + s);
        System.out.println("   " + s);
        return s;
    }
    public static void main(String[] a) throws Exception {
        ClientConnection c = new ClientConnection(new ClientConnection.ConnectionListener() {
            public void onKetNoi(String fp) { q.add("KETNOI " + fp); }
            public void onKeyOk(String s) { q.add("KEYOK " + s); }
            public void onKetQua(String r, String d) { q.add("RESULT " + r + " | " + d); }
            public void onFileOk(String s) { q.add("FILEOK " + s.replace('\n',' ')); }
            public void onLoiServer(String s) { q.add("ERROR " + s); }
            public void onNgat(String s) { q.add("NGAT " + s); }
        });
        System.out.println("1. ket noi"); c.ketNoi("localhost", Integer.getInteger("aes.test.port", 5555), 3000); cho("KETNOI");
        System.out.println("2. gui text truoc khi co khoa -> phai nem IllegalState");
        try { c.guiVanBan("x"); throw new AssertionError("khong nem"); } catch (IllegalStateException e) { System.out.println("   OK: " + e.getMessage()); }
        System.out.println("3. trao doi khoa"); c.traoDoiKhoa("khoa1"); cho("KEYOK");
        System.out.println("4. gui text"); c.guiVanBan("Xin chao AES 123");
        String r = cho("RESULT");
        if (!r.endsWith("a=2;c=1;e=1;h=1;i=1;n=1;o=1;s=1;x=1")) throw new AssertionError(r);
        String b1 = c.banMaCuoiBase64();
        c.guiVanBan("Xin chao AES 123"); cho("RESULT");
        if (b1.equals(c.banMaCuoiBase64())) throw new AssertionError("ban ma trung dù IV moi");
        System.out.println("   OK: cung ban ro, ban ma khac");
        System.out.println("5. gui tieng Viet"); c.guiVanBan("Đại Anh Dũng — Lập trình mạng"); cho("RESULT");
        for (String f : a) {
            File file = new File(f);
            System.out.println("6. gui file " + file.getName() + " " + file.length() + " byte");
            long[] last = {0}; int[] calls = {0};
            c.guiFile(file, v -> { last[0] = v; calls[0]++; });
            if (last[0] != file.length()) throw new AssertionError("tien do cuoi " + last[0]);
            System.out.println("   tien do goi " + calls[0] + " lan, cuoi = " + last[0]);
            cho("FILEOK");
        }
        System.out.println("7. doi khoa giua phien"); c.traoDoiKhoa("khoa2"); cho("KEYOK");
        c.guiVanBan("abc ABC"); r = cho("RESULT");
        if (!r.endsWith("a=2;b=2;c=2")) throw new AssertionError(r);
        System.out.println("8. gui text lien tiep khong cho"); 
        for (int i = 0; i < 5; i++) c.guiVanBan("m" + i);
        for (int i = 0; i < 5; i++) { r = cho("RESULT"); if (!r.contains("m" + i + " |")) throw new AssertionError("thu tu sai: " + r); }
        System.out.println("9. ngat chu dong"); c.ngat(); String n = cho("NGAT"); if (!n.equals("NGAT null")) throw new AssertionError(n);
        System.out.println("E2E OK");
    }
}
