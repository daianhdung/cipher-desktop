package client;

import common.AESUtil;
import common.LetterFrequency;
import common.Protocol;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Image;
import java.awt.Insets;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;
import javax.crypto.SecretKey;

/**
 * Giao diện Client — đề tài 8 (Chat + gửi file, AES, TCP).
 *
 * Năm vùng theo đúng thứ tự người dùng thao tác: kết nối → trao đổi khóa →
 * gửi văn bản / gửi file → xem kết quả. Nút của bước sau bị vô hiệu cho tới
 * khi bước trước xong ({@link #capNhatTrangThai}), nên không thể gửi khi
 * chưa có khóa.
 *
 * Quy tắc thread: mọi thao tác mạng chạy ngoài EDT (SwingWorker cho kết nối
 * và gửi file; thread nhận riêng trong {@link ClientConnection}). Callback từ
 * mạng đi qua {@link NetworkEventHandler} và được đẩy về EDT bằng invokeLater trước khi
 * đụng vào component — Swing không an toàn đa luồng.
 */
public class ClientFrame extends JFrame {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Color XANH = new Color(0, 128, 0);
    private static final Color DO = new Color(180, 0, 0);
    private static final Font MONO = new Font(Font.MONOSPACED, Font.PLAIN, 12);

    private final ClientConnection ketNoi = new ClientConnection(new NetworkEventHandler());
    private File fileDaChon;
    private boolean dangGuiFile;

    public ClientFrame() {
        initComponents();
        capNhatTrangThai();
        ghiNhatKy("Sẵn sàng. Nhập host/cổng rồi bấm Kết nối.");
    }

    // <editor-fold defaultstate="collapsed" desc="Dựng giao diện">
    private void initComponents() {
        setTitle("Đề tài 8 — Client Chat + gửi file, mã hóa AES (TCP)");
        setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                thoat();
            }
        });
        getContentPane().setLayout(new GridBagLayout());

        // ---------- 1. Kết nối ----------
        JPanel pnlKetNoi = taoPanel("1. Kết nối Server (TCP)");
        lblHost = new JLabel("Địa chỉ:");
        txtHost = new JTextField("localhost", 14);
        lblPort = new JLabel("Cổng:");
        txtPort = new JTextField(String.valueOf(Protocol.PORT_MAC_DINH), 6);
        btnKetNoi = new JButton("Kết nối");
        btnNgat = new JButton("Ngắt");
        lblTrangThai = new JLabel("● Chưa kết nối");
        lblTrangThai.setForeground(DO);
        btnKetNoi.addActionListener(e -> btnKetNoiActionPerformed());
        btnNgat.addActionListener(e -> ketNoi.ngat());
        txtPort.addActionListener(e -> btnKetNoiActionPerformed());

        int x = 0;
        pnlKetNoi.add(lblHost, gbc(x++, 0, 0, GridBagConstraints.NONE));
        pnlKetNoi.add(txtHost, gbc(x++, 0, 0, GridBagConstraints.HORIZONTAL));
        pnlKetNoi.add(lblPort, gbc(x++, 0, 0, GridBagConstraints.NONE));
        pnlKetNoi.add(txtPort, gbc(x++, 0, 0, GridBagConstraints.HORIZONTAL));
        pnlKetNoi.add(btnKetNoi, gbc(x++, 0, 0, GridBagConstraints.NONE));
        pnlKetNoi.add(btnNgat, gbc(x++, 0, 0, GridBagConstraints.NONE));
        pnlKetNoi.add(lblTrangThai, gbc(x++, 0, 1, GridBagConstraints.HORIZONTAL));

        // ---------- 2. Khóa AES ----------
        JPanel pnlKhoa = taoPanel("2. Khóa AES và trao đổi khóa (RSA-2048 OAEP)");
        lblKhoa = new JLabel("Khóa (chuỗi bất kỳ):");
        txtKhoa = new JTextField(24);
        btnTraoDoiKhoa = new JButton("Trao đổi khóa");
        lblKhoaDanXuat = new JLabel("Khóa AES-256 = SHA-256(chuỗi): —");
        lblKhoaDanXuat.setFont(MONO);
        lblRSA = new JLabel("RSA public key Server: —");
        lblRSA.setFont(MONO);
        btnTraoDoiKhoa.addActionListener(e -> btnTraoDoiKhoaActionPerformed());
        txtKhoa.addActionListener(e -> btnTraoDoiKhoaActionPerformed());

        pnlKhoa.add(lblKhoa, gbc(0, 0, 0, GridBagConstraints.NONE));
        pnlKhoa.add(txtKhoa, gbc(1, 0, 1, GridBagConstraints.HORIZONTAL));
        pnlKhoa.add(btnTraoDoiKhoa, gbc(2, 0, 0, GridBagConstraints.NONE));
        GridBagConstraints g = gbc(0, 1, 1, GridBagConstraints.HORIZONTAL);
        g.gridwidth = 3;
        pnlKhoa.add(lblRSA, g);
        g = gbc(0, 2, 1, GridBagConstraints.HORIZONTAL);
        g.gridwidth = 3;
        pnlKhoa.add(lblKhoaDanXuat, g);

        // ---------- 3. Văn bản ----------
        JPanel pnlVanBan = taoPanel("3. Văn bản → mã hóa AES → gửi Server");
        txtVanBan = new JTextArea(4, 40);
        txtVanBan.setLineWrap(true);
        txtVanBan.setWrapStyleWord(true);
        txtVanBan.setText("Xin chao AES 123");
        btnXemBanMa = new JButton("Xem bản mã");
        btnGuiVanBan = new JButton("Mã hóa và gửi");
        txtBanMa = new JTextArea(4, 40);
        txtBanMa.setEditable(false);
        txtBanMa.setLineWrap(true);
        txtBanMa.setFont(MONO);
        txtBanMa.setBackground(new Color(245, 245, 245));
        btnXemBanMa.addActionListener(e -> btnXemBanMaActionPerformed());
        btnGuiVanBan.addActionListener(e -> btnGuiVanBanActionPerformed());

        g = gbc(0, 0, 1, GridBagConstraints.BOTH);
        g.weighty = 1;
        g.gridwidth = 2;
        pnlVanBan.add(cuon(txtVanBan, "Bản rõ"), g);
        JPanel pnlNutVB = new JPanel(new java.awt.FlowLayout(java.awt.FlowLayout.RIGHT, 8, 0));
        pnlNutVB.add(btnXemBanMa);
        pnlNutVB.add(btnGuiVanBan);
        g = gbc(0, 1, 1, GridBagConstraints.HORIZONTAL);
        g.gridwidth = 2;
        pnlVanBan.add(pnlNutVB, g);
        g = gbc(0, 2, 1, GridBagConstraints.BOTH);
        g.weighty = 1;
        g.gridwidth = 2;
        pnlVanBan.add(cuon(txtBanMa, "IV + bản mã (Base64) đã/sẽ gửi"), g);

        // ---------- 4. File ----------
        JPanel pnlFile = taoPanel("4. Gửi file (hình ảnh / audio / video) — mã hóa AES theo luồng");
        btnChonFile = new JButton("Chọn file…");
        lblFile = new JLabel("Chưa chọn file");
        lblXemTruoc = new JLabel();
        lblXemTruoc.setHorizontalAlignment(SwingConstants.CENTER);
        lblXemTruoc.setPreferredSize(new Dimension(160, 100));
        lblXemTruoc.setBorder(BorderFactory.createEtchedBorder());
        prgTienDo = new JProgressBar(0, 100);
        prgTienDo.setStringPainted(true);
        btnGuiFile = new JButton("Mã hóa và gửi file");
        btnChonFile.addActionListener(e -> btnChonFileActionPerformed());
        btnGuiFile.addActionListener(e -> btnGuiFileActionPerformed());

        pnlFile.add(btnChonFile, gbc(0, 0, 0, GridBagConstraints.NONE));
        pnlFile.add(lblFile, gbc(1, 0, 1, GridBagConstraints.HORIZONTAL));
        g = gbc(2, 0, 0, GridBagConstraints.NONE);
        g.gridheight = 3;
        pnlFile.add(lblXemTruoc, g);
        g = gbc(0, 1, 1, GridBagConstraints.HORIZONTAL);
        g.gridwidth = 2;
        pnlFile.add(prgTienDo, g);
        g = gbc(0, 2, 1, GridBagConstraints.NONE);
        g.gridwidth = 2;
        g.anchor = GridBagConstraints.EAST;
        pnlFile.add(btnGuiFile, g);

        // ---------- 5. Kết quả ----------
        JPanel pnlKetQua = taoPanel("5. Kết quả Server trả về");
        mdlDem = new DefaultTableModel(new Object[]{"Chữ cái", "Số lần"}, 0) {
            @Override
            public boolean isCellEditable(int r, int c) {
                return false;
            }
        };
        tblDemChuCai = new JTable(mdlDem);
        tblDemChuCai.setFont(MONO);
        tblDemChuCai.getColumnModel().getColumn(0).setPreferredWidth(70);
        lblTong = new JLabel("Số lần xuất hiện các chữ cái trong bản rõ (Server đếm): —");
        txtNhatKy = new JTextArea(12, 36);
        txtNhatKy.setEditable(false);
        txtNhatKy.setFont(MONO);
        txtNhatKy.setLineWrap(true);
        txtNhatKy.setWrapStyleWord(true);

        pnlKetQua.add(lblTong, gbc(0, 0, 1, GridBagConstraints.HORIZONTAL));
        g = gbc(0, 1, 1, GridBagConstraints.BOTH);
        g.weighty = 1;
        JScrollPane scrBang = new JScrollPane(tblDemChuCai);
        scrBang.setPreferredSize(new Dimension(260, 180));
        pnlKetQua.add(scrBang, g);
        g = gbc(0, 2, 1, GridBagConstraints.BOTH);
        g.weighty = 1.4;
        pnlKetQua.add(cuon(txtNhatKy, "Nhật ký"), g);

        // ---------- Bố cục tổng ----------
        // Cột trái: 1, 2, 3, 4 xếp dọc. Cột phải: 5 kéo suốt chiều cao.
        g = gbc(0, 0, 1, GridBagConstraints.HORIZONTAL);
        g.insets = new Insets(8, 8, 0, 4);
        getContentPane().add(pnlKetNoi, g);
        g = gbc(0, 1, 1, GridBagConstraints.HORIZONTAL);
        g.insets = new Insets(4, 8, 0, 4);
        getContentPane().add(pnlKhoa, g);
        g = gbc(0, 2, 1, GridBagConstraints.BOTH);
        g.weighty = 1;
        g.insets = new Insets(4, 8, 0, 4);
        getContentPane().add(pnlVanBan, g);
        g = gbc(0, 3, 1, GridBagConstraints.HORIZONTAL);
        g.insets = new Insets(4, 8, 8, 4);
        getContentPane().add(pnlFile, g);
        g = gbc(1, 0, 0.7, GridBagConstraints.BOTH);
        g.gridheight = 4;
        g.weighty = 1;
        g.insets = new Insets(8, 4, 8, 8);
        getContentPane().add(pnlKetQua, g);

        pack();
        setMinimumSize(new Dimension(980, 640));
        setLocationRelativeTo(null);
    }

    private static JPanel taoPanel(String tieuDe) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBorder(BorderFactory.createTitledBorder(tieuDe));
        return p;
    }

    private static JScrollPane cuon(JTextArea ta, String tieuDe) {
        JScrollPane sp = new JScrollPane(ta);
        sp.setBorder(BorderFactory.createTitledBorder(tieuDe));
        return sp;
    }

    private static GridBagConstraints gbc(int x, int y, double weightx, int fill) {
        GridBagConstraints g = new GridBagConstraints();
        g.gridx = x;
        g.gridy = y;
        g.weightx = weightx;
        g.fill = fill;
        g.anchor = GridBagConstraints.WEST;
        g.insets = new Insets(3, 4, 3, 4);
        return g;
    }
    // </editor-fold>

    // ---------- Sự kiện ----------

    private void btnKetNoiActionPerformed() {
        final String host = txtHost.getText().trim();
        final int port;
        try {
            port = Integer.parseInt(txtPort.getText().trim());
            if (host.isEmpty() || port < 1 || port > 65535) {
                throw new NumberFormatException();
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(this, "Địa chỉ không được rỗng và cổng phải là số 1–65535.\nVui lòng nhập lại.",
                    "Thông tin kết nối không hợp lệ", JOptionPane.WARNING_MESSAGE);
            return;
        }
        btnKetNoi.setEnabled(false);
        ghiNhatKy("Đang kết nối tới " + host + ":" + port + " …");
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws Exception {
                ketNoi.ketNoi(host, port, 5000);
                return null;
            }

            @Override
            protected void done() {
                try {
                    get();
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    ghiNhatKy("Kết nối thất bại: " + t.getMessage());
                    JOptionPane.showMessageDialog(ClientFrame.this,
                            "Không kết nối được tới " + host + ":" + port + "\n" + t.getMessage()
                            + "\n\nKiểm tra Server đã chạy chưa, rồi nhập lại địa chỉ/cổng.",
                            "Kết nối thất bại", JOptionPane.ERROR_MESSAGE);
                }
                capNhatTrangThai();
            }
        }.execute();
    }

    private void btnTraoDoiKhoaActionPerformed() {
        String khoa = txtKhoa.getText();
        if (khoa.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nhập khóa trước khi trao đổi.", "Thiếu khóa", JOptionPane.WARNING_MESSAGE);
            txtKhoa.requestFocus();
            return;
        }
        btnTraoDoiKhoa.setEnabled(false);
        new SwingWorker<SecretKey, Void>() {
            protected SecretKey doInBackground() throws Exception { return ketNoi.traoDoiKhoa(khoa); }
            protected void done() {
                try {
                    String hex = AESUtil.hex(get().getEncoded());
                    lblKhoaDanXuat.setText("Khóa AES-256 = SHA-256(chuỗi nhập): " + hex.substring(0, 16) + "…");
                    ghiNhatKy("→ KEY_EXCHANGE: khóa AES đã bọc RSA-OAEP, không ghi khóa vào nhật ký");
                } catch (Exception e) { baoLoi("Trao đổi khóa thất bại", e); }
                capNhatTrangThai();
            }
        }.execute();
    }

    private void btnXemBanMaActionPerformed() {
        try {
            byte[][] r = ketNoi.xemTruocMaHoa(txtVanBan.getText());
            hienBanMa(r[0], r[1], "(xem trước, chưa gửi — mỗi lần bấm IV mới → bản mã khác)");
        } catch (Exception e) {
            baoLoi("Mã hóa thất bại", e);
        }
    }

    private void btnGuiVanBanActionPerformed() {
        String vb = txtVanBan.getText();
        if (vb.isEmpty()) {
            JOptionPane.showMessageDialog(this, "Nhập văn bản cần gửi.", "Thiếu văn bản", JOptionPane.WARNING_MESSAGE);
            return;
        }
        new SwingWorker<Void, Void>() {
            protected Void doInBackground() throws Exception { ketNoi.guiVanBan(vb); return null; }
            protected void done() {
                try { get(); hienBanMa(null, null, "(đã gửi)"); ghiNhatKy("→ TEXT: " + ketNoi.banMaCuoiDoDai() + " byte bản mã"); }
                catch (Exception e) { baoLoi("Gửi văn bản thất bại", e); }
            }
        }.execute();
    }

    private void btnChonFileActionPerformed() {
        JFileChooser fc = new JFileChooser(fileDaChon != null ? fileDaChon.getParentFile() : new File("data/mau"));
        fc.setDialogTitle("Chọn file gửi lên Server");
        fc.addChoosableFileFilter(new FileNameExtensionFilter("Hình ảnh (png, jpg, gif, bmp)", "png", "jpg", "jpeg", "gif", "bmp"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter("Audio (wav, mp3, m4a, ogg)", "wav", "mp3", "m4a", "ogg"));
        fc.addChoosableFileFilter(new FileNameExtensionFilter("Video (mp4, mov, avi, mkv)", "mp4", "mov", "avi", "mkv"));
        fc.setAcceptAllFileFilterUsed(true);
        if (fc.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        fileDaChon = fc.getSelectedFile();
        long n = fileDaChon.length();
        lblFile.setText("<html><b>" + fileDaChon.getName() + "</b><br>" + n + " byte → bản mã "
                + AESUtil.kichThuocBanMa(n) + " byte (PKCS5)</html>");
        hienXemTruoc(fileDaChon);
        prgTienDo.setValue(0);
        prgTienDo.setString("");
        ghiNhatKy("Đã chọn file " + fileDaChon.getAbsolutePath() + " (" + n + " byte)");
        capNhatTrangThai();
    }

    private void btnGuiFileActionPerformed() {
        final File f = fileDaChon;
        if (f == null || !f.isFile()) {
            JOptionPane.showMessageDialog(this, "Chọn file trước.", "Chưa có file", JOptionPane.WARNING_MESSAGE);
            return;
        }
        dangGuiFile = true;
        capNhatTrangThai();
        final long tong = Math.max(1, f.length());
        ghiNhatKy("→ FILE: " + f.getName() + " (" + f.length() + " byte) — bắt đầu mã hóa và gửi theo chunk " + Protocol.CHUNK + " byte");
        new SwingWorker<Void, Long>() {
            @Override
            protected Void doInBackground() throws Exception {
                ketNoi.guiFile(f, this::publish);
                return null;
            }

            @Override
            protected void process(java.util.List<Long> chunks) {
                long da = chunks.get(chunks.size() - 1);
                int pct = (int) (da * 100 / tong);
                prgTienDo.setValue(pct);
                prgTienDo.setString(pct + "%  (" + da + " / " + tong + " byte)");
            }

            @Override
            protected void done() {
                dangGuiFile = false;
                try {
                    get();
                    prgTienDo.setValue(100);
                    prgTienDo.setString("100% — đã gửi, chờ Server xác nhận…");
                } catch (Exception e) {
                    Throwable t = e.getCause() != null ? e.getCause() : e;
                    prgTienDo.setString("Lỗi");
                    baoLoi("Gửi file thất bại", t);
                }
                capNhatTrangThai();
            }
        }.execute();
    }

    private void thoat() {
        if (ketNoi.dangKetNoi()) {
            ketNoi.ngat();
        }
        dispose();
        System.exit(0);
    }

    // ---------- Callback từ mạng (thread khác) → EDT ----------

    private final class NetworkEventHandler implements ClientConnection.ConnectionListener {

        @Override
        public void onKetNoi(String fingerprintRSA) {
            SwingUtilities.invokeLater(() -> {
                lblTrangThai.setText("● Đã kết nối " + txtHost.getText().trim() + ":" + txtPort.getText().trim());
                lblTrangThai.setForeground(XANH);
                lblRSA.setText("RSA public key Server (SHA-256 fingerprint): " + fingerprintRSA);
                ghiNhatKy("← HELLO: nhận RSA public key 2048 bit, fingerprint " + fingerprintRSA);
                ghiNhatKy("Nhập khóa AES và bấm Trao đổi khóa.");
                capNhatTrangThai();
                txtKhoa.requestFocus();
            });
        }

        @Override
        public void onKeyOk(String thongBao) {
            SwingUtilities.invokeLater(() -> {
                ghiNhatKy("← KEY_OK: " + thongBao);
                capNhatTrangThai();
            });
        }

        @Override
        public void onKetQua(String banRo, String demChuCai) {
            SwingUtilities.invokeLater(() -> {
                Map<Character, Integer> dem = LetterFrequency.parse(demChuCai);
                mdlDem.setRowCount(0);
                for (Map.Entry<Character, Integer> e : dem.entrySet()) {
                    mdlDem.addRow(new Object[]{e.getKey(), e.getValue()});
                }
                lblTong.setText("<html>Bản rõ Server giải mã: <b>" + rutGon(banRo, 50) + "</b><br>"
                        + dem.size() + " chữ cái khác nhau, tổng " + LetterFrequency.tongChuCai(dem) + " lần</html>");
                ghiNhatKy("← RESULT: bản rõ \"" + rutGon(banRo, 40) + "\" | đếm " + demChuCai);
            });
        }

        @Override
        public void onFileOk(String thongBao) {
            SwingUtilities.invokeLater(() -> {
                prgTienDo.setString("100% — Server đã nhận và lưu");
                ghiNhatKy("← FILE_OK: " + thongBao.replace('\n', ' '));
            });
        }

        @Override
        public void onLoiServer(String loi) {
            SwingUtilities.invokeLater(() -> {
                ghiNhatKy("← ERROR: " + loi);
                JOptionPane.showMessageDialog(ClientFrame.this, loi, "Server báo lỗi", JOptionPane.ERROR_MESSAGE);
            });
        }

        @Override
        public void onNgat(String lyDo) {
            SwingUtilities.invokeLater(() -> {
                lblTrangThai.setText("● Chưa kết nối");
                lblTrangThai.setForeground(DO);
                lblRSA.setText("RSA public key Server: —");
                lblKhoaDanXuat.setText("Khóa AES-256 = SHA-256(chuỗi): —");
                ghiNhatKy(lyDo == null ? "Đã ngắt kết nối." : "Mất kết nối: " + lyDo);
                if (lyDo != null) {
                    JOptionPane.showMessageDialog(ClientFrame.this, lyDo, "Mất kết nối", JOptionPane.WARNING_MESSAGE);
                }
                dangGuiFile = false;
                capNhatTrangThai();
            });
        }
    }

    // ---------- Tiện ích ----------

    /** Bật/tắt nút theo trạng thái: chưa kết nối → chỉ Kết nối; có kết nối → Trao đổi khóa; có khóa → gửi. */
    private void capNhatTrangThai() {
        boolean kn = ketNoi.dangKetNoi();
        boolean khoa = kn && ketNoi.daCoKhoa();
        btnKetNoi.setEnabled(!kn);
        txtHost.setEnabled(!kn);
        txtPort.setEnabled(!kn);
        btnNgat.setEnabled(kn);
        txtKhoa.setEnabled(kn);
        btnTraoDoiKhoa.setEnabled(kn && !dangGuiFile);
        btnXemBanMa.setEnabled(khoa);
        btnGuiVanBan.setEnabled(khoa && !dangGuiFile);
        btnChonFile.setEnabled(!dangGuiFile);
        btnGuiFile.setEnabled(khoa && fileDaChon != null && !dangGuiFile);
    }

    private void hienBanMa(byte[] iv, byte[] banMa, String ghiChu) {
        String ivHex = iv != null ? AESUtil.hex(iv) : ketNoi.ivCuoiHex();
        String b64 = banMa != null ? Base64.getEncoder().encodeToString(banMa) : ketNoi.banMaCuoiBase64();
        int len = banMa != null ? banMa.length : ketNoi.banMaCuoiDoDai();
        txtBanMa.setText("IV (16 byte, hex): " + ivHex + "\n"
                + "Bản mã AES/CBC/PKCS5 (" + len + " byte, Base64):\n" + b64 + "\n" + ghiChu);
        txtBanMa.setCaretPosition(0);
    }

    private void hienXemTruoc(File f) {
        lblXemTruoc.setIcon(null);
        lblXemTruoc.setText(null);
        String ten = f.getName().toLowerCase();
        if (ten.matches(".*\\.(png|jpe?g|gif|bmp)$")) {
            try {
                BufferedImage img = ImageIO.read(f);
                if (img != null) {
                    int w = lblXemTruoc.getPreferredSize().width - 4;
                    int h = lblXemTruoc.getPreferredSize().height - 4;
                    double ty = Math.min((double) w / img.getWidth(), (double) h / img.getHeight());
                    Image thu = img.getScaledInstance(Math.max(1, (int) (img.getWidth() * ty)),
                            Math.max(1, (int) (img.getHeight() * ty)), Image.SCALE_SMOOTH);
                    lblXemTruoc.setIcon(new ImageIcon(thu));
                    lblXemTruoc.setToolTipText(img.getWidth() + "×" + img.getHeight());
                    return;
                }
            } catch (IOException ignore) {
                // rơi xuống hiển thị chữ
            }
            lblXemTruoc.setText("Hình ảnh");
        } else if (ten.matches(".*\\.(wav|mp3|m4a|ogg|flac)$")) {
            lblXemTruoc.setText("♪ Audio");
        } else if (ten.matches(".*\\.(mp4|mov|avi|mkv|webm)$")) {
            lblXemTruoc.setText("▶ Video");
        } else {
            lblXemTruoc.setText("File");
        }
    }

    private void ghiNhatKy(String s) {
        txtNhatKy.append("[" + LocalTime.now().format(TS) + "] " + s + "\n");
        txtNhatKy.setCaretPosition(txtNhatKy.getDocument().getLength());
    }

    private void baoLoi(String tieuDe, Throwable t) {
        ghiNhatKy(tieuDe + ": " + t.getMessage());
        JOptionPane.showMessageDialog(this, t.getMessage(), tieuDe, JOptionPane.ERROR_MESSAGE);
    }

    private static String rutGon(String s, int max) {
        s = s.replace('\n', ' ');
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }

    // ---------- Component ----------
    private JLabel lblHost, lblPort, lblTrangThai, lblKhoa, lblKhoaDanXuat, lblRSA, lblFile, lblXemTruoc, lblTong;
    private JTextField txtHost, txtPort, txtKhoa;
    private JButton btnKetNoi, btnNgat, btnTraoDoiKhoa, btnXemBanMa, btnGuiVanBan, btnChonFile, btnGuiFile;
    private JTextArea txtVanBan, txtBanMa, txtNhatKy;
    private JProgressBar prgTienDo;
    private JTable tblDemChuCai;
    private DefaultTableModel mdlDem;
}
