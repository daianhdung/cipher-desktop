package server;

import common.AESUtil;
import common.PacketIO;
import common.LetterFrequency;
import common.Protocol;
import common.RSAUtil;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.DigestOutputStream;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import javax.crypto.SecretKey;

/**
 * Server THAM CHIẾU cho đề tài 8 — đủ giao thức, KHÔNG có CSDL.
 *
 * Mục đích: (1) test Client độc lập khi Server thật chưa xong; (2) làm mẫu
 * cho bạn phụ trách Server: mọi thứ dùng chung (định dạng gói, AES, RSA,
 * đếm chữ cái) đã nằm trong package {@code common}, Server thật chỉ cần
 * thêm lớp DAO ghi {@code tin_nhan} / {@code tap_tin} tại hai chỗ đánh dấu
 * {@code // TODO CSDL}.
 *
 * Mô hình: 1 thread / client. Khóa AES là trạng thái của phiên, nên đặt
 * trong {@link ClientSession} chứ không phải biến static — hai Client dùng hai
 * khóa khác nhau vẫn chạy song song đúng.
 *
 * Chạy: java -cp build/classes server.MockServer [port]
 */
public class MockServer {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("HH:mm:ss");
    private static final Path THU_MUC_LUU = Paths.get("server_files");

    private final int port;
    private final KeyPair rsa;

    public MockServer(int port) {
        this.port = port;
        this.rsa = RSAUtil.taoCapKhoa();
    }

    public void chay() throws IOException {
        Files.createDirectories(THU_MUC_LUU);
        try (ServerSocket ss = new ServerSocket(port)) {
            log("Server lắng nghe cổng " + port + " | RSA fingerprint " + RSAUtil.fingerprint(rsa.getPublic()));
            log("File nhận được lưu vào " + THU_MUC_LUU.toAbsolutePath());
            int stt = 0;
            while (true) {
                Socket s = ss.accept();
                stt++;
                Thread t = new Thread(new ClientSession(s, "C" + stt), "phien-" + stt);
                t.start();
            }
        }
    }

    /** Một phiên làm việc với một Client. */
    private final class ClientSession implements Runnable {

        private final Socket socket;
        private final String ten;
        private SecretKey aes;   // null cho tới khi KEY_EXCHANGE thành công

        ClientSession(Socket socket, String ten) {
            this.socket = socket;
            this.ten = ten;
        }

        @Override
        public void run() {
            log(ten + " kết nối từ " + socket.getRemoteSocketAddress());
            try (Socket s = socket;
                    DataInputStream in = new DataInputStream(new BufferedInputStream(s.getInputStream()));
                    DataOutputStream out = new DataOutputStream(new BufferedOutputStream(s.getOutputStream()))) {

                PacketIO.guiMangByte(out, Protocol.HELLO, RSAUtil.encodePublic(rsa.getPublic()));

                while (true) {
                    byte loai;
                    try {
                        loai = in.readByte();
                    } catch (EOFException eof) {
                        log(ten + " đóng kết nối");
                        return;
                    }
                    switch (loai) {
                        case Protocol.KEY_EXCHANGE -> xuLyKeyExchange(in, out);
                        case Protocol.TEXT -> xuLyText(in, out);
                        case Protocol.FILE -> xuLyFile(in, out);
                        case Protocol.BYE -> {
                            log(ten + " gửi BYE");
                            return;
                        }
                        default -> {
                            PacketIO.guiChuoi(out, Protocol.ERROR, "Loại gói không hỗ trợ: " + Protocol.tenGoi(loai));
                            log(ten + " gói lạ " + Protocol.tenGoi(loai) + " -> ngắt");
                            return;
                        }
                    }
                }
            } catch (IOException e) {
                log(ten + " lỗi I/O: " + e.getMessage());
            }
        }

        private void xuLyKeyExchange(DataInputStream in, DataOutputStream out) throws IOException {
            byte[] boc = PacketIO.docBytes(in);
            try {
                aes = RSAUtil.moKhoaAES(boc, rsa.getPrivate());
                log(ten + " KEY_EXCHANGE: đã mở khóa AES-256");
                PacketIO.guiChuoi(out, Protocol.KEY_OK, "Đã nhận khóa AES-256");
            } catch (GeneralSecurityException e) {
                aes = null;
                PacketIO.guiChuoi(out, Protocol.ERROR, "Không mở được khóa AES: " + e.getMessage());
                log(ten + " KEY_EXCHANGE lỗi: " + e);
            }
        }

        private void xuLyText(DataInputStream in, DataOutputStream out) throws IOException {
            byte[] iv = PacketIO.docBytes(in);
            byte[] banMa = PacketIO.docBytes(in);
            if (aes == null) {
                PacketIO.guiChuoi(out, Protocol.ERROR, "Chưa trao đổi khóa AES");
                return;
            }
            try {
                String banRo = AESUtil.giaiMaChuoi(banMa, aes, iv);
                String dem = LetterFrequency.dem(banRo);
                log(ten + " TEXT  iv=" + AESUtil.hex(iv));
                log(ten + "       bản mã (" + banMa.length + " byte) = " + AESUtil.hex(banMa));
                log(ten + "       bản rõ = \"" + banRo + "\"");
                log(ten + "       đếm    = " + dem);
                // TODO CSDL: INSERT INTO tin_nhan(thoi_gian, iv_hex, ban_ma_hex, ban_ro, ket_qua_dem)
                PacketIO.guiResult(out, banRo, dem);
            } catch (GeneralSecurityException e) {
                PacketIO.guiChuoi(out, Protocol.ERROR, "Giải mã thất bại (sai khóa?): " + e.getMessage());
                log(ten + " TEXT giải mã lỗi: " + e);
            }
        }

        private void xuLyFile(DataInputStream in, DataOutputStream out) throws IOException {
            PacketIO.FileHeader h = PacketIO.docFileHeader(in);
            if (aes == null) {
                // vẫn phải đọc hết thân để không lệch luồng
                in.skipNBytes(h.kichThuocBanMa);
                PacketIO.guiChuoi(out, Protocol.ERROR, "Chưa trao đổi khóa AES");
                return;
            }
            String tenAnToan = Paths.get(h.tenFile).getFileName().toString().replaceAll("[^\\p{L}\\p{N}._-]", "_");
            Path dich = THU_MUC_LUU.resolve(LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")) + "_" + tenAnToan);
            log(ten + " FILE  " + h.tenFile + " gốc=" + h.kichThuocGoc + " byte, bản mã=" + h.kichThuocBanMa + " byte");
            try {
                MessageDigest sha = MessageDigest.getInstance("SHA-256");
                try (OutputStream f = new DigestOutputStream(new BufferedOutputStream(Files.newOutputStream(dich)), sha)) {
                    AESUtil.giaiMaLuong(in, f, aes, h.iv, h.kichThuocBanMa, null);
                }
                long thuc = Files.size(dich);
                String hex = AESUtil.hex(sha.digest());
                if (thuc != h.kichThuocGoc) {
                    throw new IOException("kích thước sau giải mã " + thuc + " ≠ khai báo " + h.kichThuocGoc);
                }
                log(ten + "       đã lưu " + dich + " sha256=" + hex);
                // TODO CSDL: INSERT INTO tap_tin(thoi_gian, ten, kich_thuoc, sha256, duong_dan)
                PacketIO.guiChuoi(out, Protocol.FILE_OK,
                        "Đã lưu " + dich.toAbsolutePath() + " (" + thuc + " byte)\nSHA-256: " + hex);
            } catch (GeneralSecurityException | IOException e) {
                // Sai khóa -> doFinal ném BadPadding sau khi đã đọc hết thân file,
                // nên luồng vẫn thẳng hàng cho gói sau: báo lỗi và tiếp tục phiên.
                Files.deleteIfExists(dich);
                PacketIO.guiChuoi(out, Protocol.ERROR, "Nhận file thất bại: " + e.getMessage());
                log(ten + " FILE lỗi: " + e);
            }
        }
    }

    private static void log(String s) {
        System.out.println("[" + LocalDateTime.now().format(TS) + "] " + s);
    }

    public static void main(String[] args) throws IOException {
        int port = args.length > 0 ? Integer.parseInt(args[0]) : Protocol.PORT_MAC_DINH;
        new MockServer(port).chay();
    }
}
