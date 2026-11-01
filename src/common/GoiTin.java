package common;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * Đọc/ghi các phần tử của gói tin trên DataInputStream/DataOutputStream.
 *
 * Chỉ có 2 kiểu payload: chuỗi (writeUTF/readUTF, JDK tự thêm 2 byte độ dài)
 * và mảng byte (int độ dài + bytes). Gom vào một chỗ để Client và Server
 * không bao giờ lệch nhau về thứ tự/định dạng.
 *
 * Mọi hàm ở đây đều đồng bộ (blocking). Bên gọi phải flush sau khi ghi xong
 * một gói trọn vẹn; ghi nửa gói rồi flush sẽ làm bên nhận chờ mãi.
 */
public final class GoiTin {

    private GoiTin() {
    }

    public static void ghiBytes(DataOutputStream out, byte[] data) throws IOException {
        out.writeInt(data.length);
        out.write(data);
    }

    public static byte[] docBytes(DataInputStream in) throws IOException {
        int len = in.readInt();
        if (len < 0 || len > Protocol.MAX_BYTES) {
            throw new IOException("Độ dài mảng byte không hợp lệ: " + len);
        }
        byte[] data = new byte[len];
        in.readFully(data);
        return data;
    }

    // ---- Gói không payload / payload chuỗi ----

    public static void guiLoai(DataOutputStream out, byte loai) throws IOException {
        out.writeByte(loai);
        out.flush();
    }

    public static void guiChuoi(DataOutputStream out, byte loai, String s) throws IOException {
        out.writeByte(loai);
        out.writeUTF(s);
        out.flush();
    }

    public static void guiMangByte(DataOutputStream out, byte loai, byte[] data) throws IOException {
        out.writeByte(loai);
        ghiBytes(out, data);
        out.flush();
    }

    // ---- TEXT / RESULT ----

    public static void guiText(DataOutputStream out, byte[] iv, byte[] banMa) throws IOException {
        out.writeByte(Protocol.TEXT);
        ghiBytes(out, iv);
        ghiBytes(out, banMa);
        out.flush();
    }

    public static void guiResult(DataOutputStream out, String banRo, String demChuCai) throws IOException {
        out.writeByte(Protocol.RESULT);
        out.writeUTF(banRo);
        out.writeUTF(demChuCai);
        out.flush();
    }

    // ---- FILE header (phần thân stream do bên gọi tự ghi) ----

    public static void guiFileHeader(DataOutputStream out, String tenFile, long kichThuocGoc,
            byte[] iv, long kichThuocBanMa) throws IOException {
        out.writeByte(Protocol.FILE);
        out.writeUTF(tenFile);
        out.writeLong(kichThuocGoc);
        ghiBytes(out, iv);
        out.writeLong(kichThuocBanMa);
        // KHÔNG flush ở đây: thân file đi liền sau, flush một lần khi xong.
    }

    /** Header của gói FILE sau byte loại. */
    public static final class FileHeader {
        public final String tenFile;
        public final long kichThuocGoc;
        public final byte[] iv;
        public final long kichThuocBanMa;

        FileHeader(String tenFile, long kichThuocGoc, byte[] iv, long kichThuocBanMa) {
            this.tenFile = tenFile;
            this.kichThuocGoc = kichThuocGoc;
            this.iv = iv;
            this.kichThuocBanMa = kichThuocBanMa;
        }
    }

    public static FileHeader docFileHeader(DataInputStream in) throws IOException {
        String ten = in.readUTF();
        long goc = in.readLong();
        byte[] iv = docBytes(in);
        long banMa = in.readLong();
        if (goc < 0 || banMa != AESUtil.kichThuocBanMa(goc)) {
            throw new IOException("Header FILE không hợp lệ: goc=" + goc + ", banMa=" + banMa);
        }
        return new FileHeader(ten, goc, iv, banMa);
    }
}
