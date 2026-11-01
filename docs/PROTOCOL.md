# TCP protocol AESCHAT/2

Đặc tả v1 giữ tại [DoAn_PROTOCOL.md](DoAn_PROTOCOL.md). v1 và v2 không tương thích
trên cùng socket. Client/Server v2 từ chối phiên bản khác ngay handshake.

## Handshake

Mọi số là big-endian. `bytes` = int32 length + dữ liệu; `string` = bytes UTF-8 chuẩn.
Không dùng Java object serialization. Frame clear handshake: int32 length + Packet.

1. Server gửi HELLO: `id=session UUID`, `text=AESCHAT/2`, `size=2`, `extra=RSA fingerprint`,
   `data=X.509 RSA public key`.
2. Client gửi JOIN: `text=username`, `size=2`, `data=RSA-OAEP(session AES key)`.
3. Server trả JOINED dưới dạng frame mã hóa; tên trùng trả ERROR rồi đóng socket.
4. Server gửi USERS (danh sách username online ngăn bằng newline), cập nhật khi join/leave.

Handshake timeout 15 giây Server / 10 giây Client (TCP connect 5 giây).
Fingerprint hiển thị để đối chiếu trực tiếp, không có PKI/pinning tự động.

## Frame mã hóa

```
int32 frameLength
int64 sequence
byte[12] randomNonce
byte[...] AES-GCM ciphertext
byte[16] GCM tag
```

AAD UTF-8 = `AESCHAT/2/<session UUID>/<direction>/<sequence>`; direction `C` hoặc `S`.
Sequence riêng mỗi hướng, bắt đầu 0, tăng đúng 1; không reset khi đổi khóa. Tag sai,
sequence lặp/sai, EOF giữa frame, length âm/quá giới hạn đều đóng phiên đó.

Packet sau giải mã luôn theo thứ tự:

```
string type, id, from, to, text, extra
int64 size
int32 index
bytes data
```

Giới hạn: frame 128 KiB, mỗi string 32768 byte UTF-8, `data` 64 KiB. TEXT/LAB tối đa
8192 byte UTF-8 để cả kết quả đếm cũng nằm trong giới hạn. Đọc length trước, kiểm
tra rồi cấp phát; dùng `readFully`. Header và mọi field được bảo vệ bởi GCM.
Packet không được thêm trailing bytes. `from` Client gửi bị bỏ qua khi định tuyến.

## Các loại gói

| Loại | Hướng | Nội dung chính |
|---|---|---|
| JOINED | S→C | id phiên, text username |
| USERS | S→C | text danh sách online |
| CONTACTS | C↔S | danh sách tên đã biết trong DB (tối đa 200) |
| TEXT | C→S | id request UUID, to người nhận, text bản rõ bên trong GCM |
| LAB | C→S | id request, text phân tích độc lập, không gửi tới người khác |
| RESULT | S→C | id request, text bản rõ, extra thống kê chữ cái |
| CHAT | S→C | id message do Server tạo, from/to, text, extra timestamp UTC |
| HISTORY | C→S | id request, to người đối thoại |
| HISTORY_ITEM | S→C | id message, from/to, text hoặc cảnh báo thiếu khóa, extra UTC, data UTF-8 thống kê |
| HISTORY_FILE | S→C | id attachment, from/to, text tên, size, extra `UTC\|status\|hash` |
| HISTORY_END | S→C | id request, to người đối thoại |
| ROTATE | C→S | id request, data khóa mới bọc RSA; toàn gói vẫn mã hóa khóa cũ |
| ROTATED | S→C | id request, mã hóa bằng khóa mới |
| PING/PONG | C↔S | heartbeat |
| ERROR | S→C | id liên quan, text lỗi; lỗi nghiệp vụ giữ phiên |
| BYE | C→S | đóng phiên; Client cũng có thể đóng socket ngay |

Server lưu + đếm TEXT trước khi route CHAT, gửi RESULT cho người gửi. Lab dùng cùng
logic nhưng không route. Người nhận offline trả ERROR; không có queue offline.
Lịch sử tối đa 200 message gần nhất + 100 attachment gần nhất, sắp theo thời gian.
Không dùng `from` hay đường dẫn do Client cung cấp để truy xuất file.

## File theo chunk

| Loại | Nội dung |
|---|---|
| FILE_BEGIN C→S | id request, to recipient, text tên gốc, size kích thước 0–1 GiB |
| FILE_READY S→C | id request, text UUID upload do Server cấp, extra tên đã làm sạch, to, size |
| FILE_CHUNK C→S | id upload, index tăng từ 0, data 1–65536 byte |
| FILE_END C→S | id upload, text SHA-256 hex của bản gốc |
| FILE_CANCEL C→S | id upload thuộc chính phiên gửi |
| FILE_START S→C | id attachment, from/to, text tên, size, extra SHA-256 |
| FILE_DATA S→C | id attachment, index từ 0, data |
| FILE_FINISH S→C | id attachment, text SHA-256 |
| FILE_ACK C→S | id attachment đang được tải trong chính phiên, text hash đã nhận hoặc FAILED |
| FILE_STATUS S→C | id attachment, text trạng thái cho sender/recipient |
| FILE_ABORT S→C | id attachment, xóa .part ở recipient |
| FILE_GET C→S | id attachment, Server kiểm user là sender/recipient trong DB |

Mỗi chunk mã hóa riêng bằng khóa phiên tương ứng. Server giải mã upload, tính hash,
đóng `.part`, rename UUID `.bin`, rồi mã hóa lại các chunk cho phiên nhận. Không có
một GCM cipher bao toàn file (tránh provider giữ file lớn trong RAM đến khi kiểm tag).
Kích thước, thứ tự chunk và hash tổng ngăn thiếu/lặp/đảo chunk. Client chỉ công bố
file nhận hoàn chỉnh sau hash khớp; tên file và path lưu là hai khái niệm riêng.

## Audit mật mã

v1: AES-256/CBC/PKCS5Padding, IV ngẫu nhiên 16 byte, không MAC; chỉ giữ làm regression.
v2: AES-256/GCM/NoPadding, nonce SecureRandom 12 byte mới mỗi record, tag 128 bit.
Chat mặc định dùng khóa AES 32 byte ngẫu nhiên. Lab giữ SHA-256(UTF-8 chuỗi nhập)
từ AESUtil để đáp ứng yêu cầu nhập khóa; đây không phải password KDF chống vét cạn,
vì vậy dùng chuỗi dài/ngẫu nhiên khi demo khóa tự nhập.
RSA dùng utility cũ: 2048-bit, OAEPWithSHA-256AndMGF1Padding; trên SunJCE Java 21,
hash OAEP SHA-256 và MGF1 mặc định SHA-1. Hai đầu dùng cùng provider Java 21;
không ngầm tuyên bố MGF1 là SHA-256. RSA keypair sinh lại khi Server restart, private
key chỉ ở RAM. Không persist session keys. Xem DATABASE.md về lịch sử sau restart.

Đổi khóa: Client chặn gửi mới ngoài EDT, gửi ROTATE khóa cũ; Server đổi receive key,
queue chuyển write key + ROTATED sau các frame cũ. Client vẫn đọc khóa cũ cho đến
frame đầu hợp lệ với pending key, commit write key trước khi mở barrier. ACK/file
và heartbeat cũng đi qua barrier. Timeout đổi khóa đóng phiên để tránh lệch khóa.
GCM không xác thực danh tính Server: kẻ MITM có thể thay public key nếu không đối
chiếu fingerprint ngoài băng. Username cũng không phải authentication.

Tài liệu tham khảo: [JDK 21 Cipher / GCM](https://docs.oracle.com/en/java/javase/21/docs/api/java.base/javax/crypto/Cipher.html).
