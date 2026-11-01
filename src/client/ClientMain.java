package client;

import javax.swing.SwingUtilities;
import javax.swing.UIManager;

/**
 * Điểm vào Client. Look & Feel hệ thống để cửa sổ trông giống ứng dụng
 * native trên máy chạy; toàn bộ dựng GUI nằm trong EDT theo quy tắc Swing.
 *
 * Chạy: java -cp build/classes client.ClientMain
 * (Server tham chiếu: java -cp build/classes server.MockServer)
 */
public class ClientMain {

    public static void main(String[] args) {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception ignore) {
            // giữ Metal mặc định
        }
        boolean legacy = java.util.Arrays.asList(args).contains("--legacy");
        SwingUtilities.invokeLater(() -> {
            if (legacy) new ClientFrame().setVisible(true);
            else new chat.client.ChatFrame().setVisible(true);
        });
    }
}
