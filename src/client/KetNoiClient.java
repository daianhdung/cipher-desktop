package client;

import common.AESUtil;
import common.GoiTin;
import common.Protocol;
import common.RSAUtil;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Base64;
import java.util.function.LongConsumer;
import javax.crypto.SecretKey;

/**
 * Phần mạng của Client: socket TCP, handshake, gửi TEXT/FILE, thread nhận.
 *
 * Tách khỏi {@link ClientFrame} để GUI không biết gì về byte và socket, còn
 * lớp này không biết gì về Swing. Giao tiếp ngược lên GUI qua {@link NguoiNghe};
 * mọi callback được gọi từ thread nhận (hoặc thread gửi file), KHÔNG phải
 * EDT — bên GUI phải tự bọc {@code SwingUtilities.invokeLater}.
 *
 * Gửi và nhận đi trên hai thread khác nhau nhưng dùng chung một socket:
 * ghi được khóa bằng {@code synchronized(out)} để header FILE và thân file
 * không bị xen gói TEXT vào giữa; đọc chỉ có một thread nên không cần khóa.
 */
public class KetNoiClient {

    /** Sự kiện từ mạng đẩy lên GUI. */
    public interface NguoiNghe {

        void onKetNoi(String fingerprintRSA);

        void onKeyOk(String thongBao);

        void onKetQua(String banRo, String demChuCai);

        void onFileOk(String thongBao);

        void onLoiServer(String loi);

        /** Kết nối đã đóng (do ta ngắt, Server đóng, hoặc lỗi I/O — lyDo có thể null nếu chủ động). */
        void onNgat(String lyDo);
    }

    private final NguoiNghe nghe;
    private Socket socket;
    private DataInputStream in;
    private DataOutputStream out;
    private PublicKey rsaServer;
    private volatile SecretKey aes;
    private volatile boolean chuDongNgat;

    /** IV + bản mã của lần gửi TEXT gần nhất — GUI hiển thị để chụp ảnh báo cáo. */
    private byte[] ivCuoi;
    private byte[] banMaCuoi;

    public KetNoiClient(NguoiNghe nghe) {
        this.nghe = nghe;
    }

    // ---- Kết nối ----

    /** Mở TCP, nhận HELLO (RSA public key). Blocking — gọi ngoài EDT. */
    public void ketNoi(String host, int port, int timeoutMs) throws IOException, GeneralSecurityException {
        Socket s = new Socket();
        s.connect(new InetSocketAddress(host, port), timeoutMs);
        s.setSoTimeout(timeoutMs);      // chỉ cho pha handshake
        DataInputStream i = new DataInputStream(new BufferedInputStream(s.getInputStream()));
        DataOutputStream o = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()));

        byte loai = i.readByte();
        if (loai != Protocol.HELLO) {
            s.close();
            throw new IOException("Server không gửi HELLO (nhận " + Protocol.tenGoi(loai) + ")");
        }
        PublicKey pub = RSAUtil.decodePublic(GoiTin.docBytes(i));
        s.setSoTimeout(0);              // sau đó chờ vô hạn, thread nhận tự thoát khi socket đóng

        this.socket = s;
        this.in = i;
        this.out = o;
        this.rsaServer = pub;
        this.aes = null;
        this.chuDongNgat = false;

        Thread t = new Thread(this::vongNhan, "client-nhan");
        t.setDaemon(true);
        t.start();
        nghe.onKetNoi(RSAUtil.fingerprint(pub));
    }

    public boolean dangKetNoi() {
        return socket != null && socket.isConnected() && !socket.isClosed();
    }

    public boolean daCoKhoa() {
        return aes != null;
    }

    public void ngat() {
        chuDongNgat = true;
        // Closing immediately also interrupts a blocked file write; never wait for out's lock.
        dongSocket();
    }

    private void dongSocket() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignore) {
        }
    }

    // ---- Gửi ----

    /** Dẫn xuất khóa AES từ chuỗi, bọc RSA, gửi KEY_EXCHANGE. Trả về khóa để GUI hiện hex. */
    public SecretKey traoDoiKhoa(String khoaNguoiDung) throws IOException, GeneralSecurityException {
        SecretKey k = AESUtil.taoKhoa(khoaNguoiDung);
        byte[] boc = RSAUtil.bocKhoaAES(k, rsaServer);
        synchronized (out) {
            GoiTin.guiMangByte(out, Protocol.KEY_EXCHANGE, boc);
        }
        aes = k;    // dùng ngay; nếu Server trả ERROR thì GUI sẽ báo
        return k;
    }

    /** Mã hóa nhưng chưa gửi — cho nút "Xem bản mã". Trả về IV + bản mã. */
    public byte[][] xemTruocMaHoa(String vanBan) throws GeneralSecurityException {
        kiemTraKhoa();
        byte[] iv = AESUtil.taoIV();
        return new byte[][]{iv, AESUtil.maHoaChuoi(vanBan, aes, iv)};
    }

    public void guiVanBan(String vanBan) throws IOException, GeneralSecurityException {
        kiemTraKhoa();
        byte[] iv = AESUtil.taoIV();
        byte[] banMa = AESUtil.maHoaChuoi(vanBan, aes, iv);
        synchronized (out) {
            GoiTin.guiText(out, iv, banMa);
        }
        ivCuoi = iv;
        banMaCuoi = banMa;
    }

    /**
     * Gửi file: header rồi stream bản mã theo chunk. Blocking, gọi từ
     * SwingWorker. {@code tienDo} nhận số byte bản rõ đã gửi.
     */
    public void guiFile(File f, LongConsumer tienDo) throws IOException, GeneralSecurityException {
        kiemTraKhoa();
        long kichThuoc = f.length();
        byte[] iv = AESUtil.taoIV();
        long banMa = AESUtil.kichThuocBanMa(kichThuoc);
        synchronized (out) {
            GoiTin.guiFileHeader(out, f.getName(), kichThuoc, iv, banMa);
            try (InputStream fin = new BufferedInputStream(Files.newInputStream(f.toPath()))) {
                AESUtil.maHoaLuong(fin, out, aes, iv, kichThuoc, tienDo);
            }
            out.flush();
        }
    }

    private void kiemTraKhoa() {
        if (aes == null) {
            throw new IllegalStateException("Chưa trao đổi khóa AES với Server");
        }
    }

    // ---- Nhận ----

    private void vongNhan() {
        String lyDo = null;
        try {
            while (true) {
                byte loai = in.readByte();
                switch (loai) {
                    case Protocol.KEY_OK -> nghe.onKeyOk(in.readUTF());
                    case Protocol.RESULT -> {
                        String banRo = in.readUTF();
                        String dem = in.readUTF();
                        nghe.onKetQua(banRo, dem);
                    }
                    case Protocol.FILE_OK -> nghe.onFileOk(in.readUTF());
                    case Protocol.ERROR -> nghe.onLoiServer(in.readUTF());
                    default -> throw new IOException("Gói lạ từ Server: " + Protocol.tenGoi(loai));
                }
            }
        } catch (EOFException e) {
            lyDo = chuDongNgat ? null : "Server đã đóng kết nối";
        } catch (SocketException e) {
            lyDo = chuDongNgat ? null : "Mất kết nối: " + e.getMessage();
        } catch (IOException e) {
            lyDo = "Lỗi nhận dữ liệu: " + e.getMessage();
        } finally {
            dongSocket();
            aes = null;
            nghe.onNgat(lyDo);
        }
    }

    // ---- Cho GUI hiển thị ----

    public String ivCuoiHex() {
        return ivCuoi == null ? "" : AESUtil.hex(ivCuoi);
    }

    public String banMaCuoiBase64() {
        return banMaCuoi == null ? "" : Base64.getEncoder().encodeToString(banMaCuoi);
    }

    public int banMaCuoiDoDai() {
        return banMaCuoi == null ? 0 : banMaCuoi.length;
    }
}
