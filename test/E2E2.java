import client.KetNoiClient;
import java.util.concurrent.*;
public class E2E2 {
    public static void main(String[] a) throws Exception {
        BlockingQueue<String> q = new LinkedBlockingQueue<>();
        KetNoiClient c = new KetNoiClient(new KetNoiClient.NguoiNghe() {
            public void onKetNoi(String fp) { q.add("KETNOI"); }
            public void onKeyOk(String s) { q.add("KEYOK"); }
            public void onKetQua(String r, String d) { q.add("RESULT"); }
            public void onFileOk(String s) { q.add("FILEOK"); }
            public void onLoiServer(String s) { q.add("ERROR " + s); }
            public void onNgat(String s) { q.add("NGAT " + s); }
        });
        c.ketNoi("localhost", Integer.getInteger("aes.test.port", 5555), 3000); System.out.println(q.take());
        c.traoDoiKhoa("k"); System.out.println(q.take());
        Runtime.getRuntime().exec(new String[]{"kill", a[0]}).waitFor();
        String s = q.poll(5, TimeUnit.SECONDS); System.out.println(s);
        if (s == null || !s.startsWith("NGAT Server")) throw new AssertionError(s);
        System.out.println("dangKetNoi=" + c.dangKetNoi() + " daCoKhoa=" + c.daCoKhoa());
        try { c.ketNoi("localhost", Integer.getInteger("aes.test.port", 5555), 1500); throw new AssertionError("phai that bai"); } catch (java.io.IOException e) { System.out.println("ket noi lai that bai dung: " + e.getMessage()); }
        System.out.println("E2E2 OK");
    }
}
