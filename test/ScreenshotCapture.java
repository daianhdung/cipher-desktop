import client.ClientFrame;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.lang.reflect.*;
import javax.imageio.ImageIO;
import javax.swing.*;

/** Điều khiển ClientFrame qua reflection và chụp ảnh nội dung cửa sổ ra PNG (không cần quyền Screen Recording). */
public class ScreenshotCapture {
    static ClientFrame f; static File out;
    static Object field(String n) throws Exception { Field x = ClientFrame.class.getDeclaredField(n); x.setAccessible(true); return x.get(f); }
    static void set(String n, Object v) throws Exception { Field x = ClientFrame.class.getDeclaredField(n); x.setAccessible(true); x.set(f, v); }
    static void call(String n, Class<?>[] t, Object... a) throws Exception {
        Method m = ClientFrame.class.getDeclaredMethod(n, t); m.setAccessible(true);
        SwingUtilities.invokeAndWait(() -> { try { m.invoke(f, a); } catch (Exception e) { throw new RuntimeException(e); } });
    }
    static void call(String n) throws Exception { call(n, new Class<?>[0]); }
    static void chup(String ten) throws Exception {
        Thread.sleep(400);
        SwingUtilities.invokeAndWait(() -> {
            try {
                int s = 2, w = f.getContentPane().getWidth(), h = f.getContentPane().getHeight();
                BufferedImage img = new BufferedImage(w * s, h * s, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics(); g.scale(s, s);
                g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
                f.getContentPane().printAll(g); g.dispose();
                ImageIO.write(img, "png", new File(out, ten)); System.out.println("chụp " + ten);
            } catch (Exception e) { throw new RuntimeException(e); }
        });
    }
    public static void main(String[] a) throws Exception {
        out = new File(a[0]); out.mkdirs(); File anh = new File(a[1]); File lon = new File(a[2]);
        UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        SwingUtilities.invokeAndWait(() -> { f = new ClientFrame(); f.setSize(1180, 720); f.setLocationRelativeTo(null); f.setVisible(true); });
        Thread.sleep(800); chup("01-khoi-dong.png");
        call("btnKetNoiActionPerformed"); Thread.sleep(1500); chup("02-da-ket-noi.png");
        SwingUtilities.invokeAndWait(() -> { try { ((JTextField) field("txtKhoa")).setText("khoa bi mat 2026"); } catch (Exception e) { throw new RuntimeException(e); } });
        call("btnTraoDoiKhoaActionPerformed"); Thread.sleep(600); chup("03-trao-doi-khoa.png");
        call("btnXemBanMaActionPerformed"); Thread.sleep(300); chup("04-xem-ban-ma.png");
        call("btnGuiVanBanActionPerformed"); Thread.sleep(800); chup("05-ket-qua-dem.png");
        // gửi lại cùng văn bản -> bản mã khác
        call("btnGuiVanBanActionPerformed"); Thread.sleep(800); chup("05b-gui-lai-iv-khac.png");
        // văn bản tiếng Việt
        SwingUtilities.invokeAndWait(() -> { try { ((JTextArea) field("txtVanBan")).setText("Lập trình mạng — Đề tài 8: Chat và gửi file mã hóa AES"); } catch (Exception e) { throw new RuntimeException(e); } });
        call("btnGuiVanBanActionPerformed"); Thread.sleep(800); chup("06-tieng-viet.png");
        // chọn file ảnh (bỏ qua JFileChooser)
        SwingUtilities.invokeAndWait(() -> { try {
            set("fileDaChon", anh);
            ((JLabel) field("lblFile")).setText("<html><b>" + anh.getName() + "</b><br>" + anh.length() + " byte → bản mã " + common.AESUtil.kichThuocBanMa(anh.length()) + " byte (PKCS5)</html>");
            Method m = ClientFrame.class.getDeclaredMethod("hienXemTruoc", File.class); m.setAccessible(true); m.invoke(f, anh);
            Method c = ClientFrame.class.getDeclaredMethod("capNhatTrangThai"); c.setAccessible(true); c.invoke(f);
        } catch (Exception e) { throw new RuntimeException(e); } });
        chup("07-chon-file-anh.png");
        call("btnGuiFileActionPerformed"); Thread.sleep(1500); chup("08-gui-file-anh-xong.png");
        // file lớn: chụp giữa lúc gửi để thấy progress
        SwingUtilities.invokeAndWait(() -> { try {
            set("fileDaChon", lon);
            ((JLabel) field("lblFile")).setText("<html><b>" + lon.getName() + "</b><br>" + lon.length() + " byte → bản mã " + common.AESUtil.kichThuocBanMa(lon.length()) + " byte (PKCS5)</html>");
            Method m = ClientFrame.class.getDeclaredMethod("hienXemTruoc", File.class); m.setAccessible(true); m.invoke(f, lon);
            Method c = ClientFrame.class.getDeclaredMethod("capNhatTrangThai"); c.setAccessible(true); c.invoke(f);
        } catch (Exception e) { throw new RuntimeException(e); } });
        Method g = ClientFrame.class.getDeclaredMethod("btnGuiFileActionPerformed"); g.setAccessible(true);
        SwingUtilities.invokeLater(() -> { try { g.invoke(f); } catch (Exception e) { throw new RuntimeException(e); } });
        Thread.sleep(60); chup("09-gui-file-lon-dang-chay.png");
        Thread.sleep(4000); chup("10-gui-file-lon-xong.png");
        System.out.println("XONG"); System.exit(0);
    }
}
