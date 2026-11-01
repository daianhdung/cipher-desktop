# Trạng thái triển khai — 2026-11-01

Đã nâng cấp tăng dần từ project AES cũ. Bản v1 và regression được giữ; v2 là đường
chạy mặc định qua `client.ClientMain` và `chat.server.ServerMain`.

## Ma trận hoàn thành

| Yêu cầu | Kết quả |
|---|---|
| FR-01: tên, UUID phiên, chống trùng, ngắt an toàn | Đã làm, test TCP và Swing |
| FR-02: danh sách online/offline | Đã làm, cập nhật JOIN/leave/timeout; lịch sử giữ tên offline |
| FR-03: chat riêng + thống kê + Lab độc lập | Đã làm, Alice/Bob/Charlie và gửi đồng thời |
| FR-04: AES/RSA, nhập khóa, integrity, rotation | AES-GCM v2; RSA utility được giữ; barrier đổi khóa; audit trong PROTOCOL |
| FR-05: file tới recipient, progress, save, SHA-256 | Đã làm, stream 64 KiB, store-and-forward, cancel, authorized history retrieval |
| FR-06: relational DB | SQLite JDBC, transaction, metadata/ciphertext/statistics/file status/session |
| FR-07: lịch sử | Đã làm; 200 message + 100 attachment; nội dung chỉ giải mã khi còn key trong RAM |
| FR-08: đếm chữ cái tiếng Việt | Giữ utility cũ; trả RESULT và persist, lịch sử cũng trả thống kê |
| GUI | Chat / Encryption Lab / Nhật ký; mọi network I/O ngoài EDT |
| Server view | Console RUNNING/count, JOIN/OFFLINE, status/sessions/quit |
| Packaging | Hai executable JAR + lib, alias DoAn_ChatAES.jar, script build/test |
| LAN 2 laptop | Hướng dẫn đã có; **chưa thử thực tế hai máy** |

## Kết quả test thực tế

Môi trường: macOS, Oracle Java 21.0.7, Python 3.9. Test mạng dùng port ngẫu nhiên và
dữ liệu tạm. Không dùng cùng thư mục DB với phiên người dùng.

- `common.CommonSelfTest`: PASS toàn bộ AES-CBC/RSA/stream/count regression.
- `ChatIntegration` (heap 128 MiB): PASS GCM Unicode, tag lỗi, frame quá lớn/đọc phân mảnh,
  ba kết nối, tên trùng, chat riêng/reply, 20 gửi đồng thời, rotate, Lab, nonce mới,
  file 32 MiB + rotate trong upload, file rỗng, SHA-256, quyền FILE_GET, cancel,
  disconnect isolation, lịch sử và restart DB/file/statistics.
- `ChatFaults` (heap 128 MiB): PASS metadata quá lớn, sai chunk, sai session key/GCM,
  replay, truncated connection, ép JDBC SQLITE_BUSY rồi phục hồi, không lưu bản rõ
  message, xóa .part, active Server shutdown và connection refused.
- `scripts/process_test.py`: PASS executable Server JAR + Alice/Bob/Charlie ở ba
  JVM riêng; private reply, ảnh/audio/video nhận đúng byte/hash, Charlie không nhận.
- `E2E`, `E2E2`: PASS regression TCP v1 và đối chiếu SHA-256 ba media mẫu.
- `ChatGuiIntegration` (desktop thật, heap 128 MiB): PASS mở hai JFrame, connect và
  hiển thị tin gửi từ GUI; Timer vẫn tick khi bắt đầu upload 256 MiB + yêu cầu rotate;
  handler Ngắt <500 ms. Upload này được chủ động ngắt, **không tuyên bố chuyển xong 256 MiB**.
- Screenshot kiểm tra giao diện: `DoAn_ChatAES/build/chat-gui.png`.

Trong lúc hoàn thiện đã sửa race accept/shutdown, ACK file/rotation, callback GUI
phiên cũ và test presence bắt nhầm snapshot trước khi Charlie join. Test cuối chờ
đúng event sau disconnect. Không bỏ qua assertion để biến lỗi thành PASS.

Log chạy cuối được giữ ở `DoAn_ChatAES/build/reports/network.log` và `gui.log`.
Kịch bản manual Save As/overwrite, font/scale Windows/Linux và **hai laptop LAN**
vẫn cần thực hiện theo TEST_PLAN và DEMO_GUIDE; không gộp vào kết quả tự động.

## Nhóm file thay đổi

- Thêm `src/chat/common/{Packet,Wire,Crypto,Channel}.java`: framing v2 và GCM queue.
- Thêm `src/chat/server/{ChatServer,ChatRepository,ServerMain}.java`: phiên/routing,
  store-and-forward, SQLite, console/config bind.
- Thêm `src/chat/client/{ChatClient,ChatFrame}.java`: network API và giao diện mới.
- Sửa `src/client/ClientMain.java`: mặc định GUI mới, `--legacy` giữ GUI cũ.
- Sửa `src/client/{ClientFrame,ClientConnection}.java`: gửi ngoài EDT, ngắt không chờ writer.
- Sửa `src/server/MockServer.java`: bỏ log khóa thô; sửa chú thích `RSAUtil` về Server identity.
- Thêm bốn test v2 và ba script build/test/process; test v1 thêm configurable test port.
- Thêm SQLite/SLF4J JAR, checksum pin, license notices; NetBeans runtime classpath.
- Thêm/tái sử dụng tám tài liệu yêu cầu, kiến trúc, protocol, DB, sequence, test, demo,
  status; tài liệu Client/protocol cũ được đánh dấu legacy, sửa CSDL thành bắt buộc.

## Giới hạn cần biết trước demo

1. Username là định danh tự khai, không password authentication. Người khác có thể
   nhận username cũ để truy cập lịch sử/file; chỉ dùng LAN demo tin cậy.
2. Không E2EE, Server đọc bản rõ để đếm. RSA public key chưa có chứng chỉ/pinning;
   đối chiếu fingerprint ngoài băng, không coi GCM là xác thực danh tính Server.
3. Khóa phiên chỉ ở RAM (cache tối đa 1024); restart/eviction làm text lịch sử không
   giải mã được. Metadata, thống kê, attachment vẫn còn và được hiển thị rõ.
4. File Server/Client lưu bản rõ trên disk; cần bảo vệ thư mục OS. Không có retention,
   tổng disk quota, resume, offline queue hay redelivery tự động.
5. Chat echo xác nhận lưu + enqueue, không phải read/delivery receipt. File có ACK hash.
6. Mỗi file tối đa 1 GiB, 2 upload/4 download mỗi phiên, 100 socket; queue chậm quá
   3 giây sẽ đóng phiên. Một progress bar GUI hiển thị chuyển file gần nhất.
7. Khóa Lab dùng SHA-256 chuỗi nhập để giữ bài cũ, không phải password KDF. Chat mặc
   định dùng khóa ngẫu nhiên. V1 CBC vẫn không có integrity, chỉ dùng regression.
8. Chỉ chạy một Server trên một thư mục --data. Chưa có benchmark tải lớn hay kiểm
   thử trên hai laptop thật; không coi bản đồ án này là dịch vụ chat production.

## Chạy nhanh

Từ thư mục `DoAn_ChatAES`:

```bash
python3 scripts/build.py
# Terminal Server
java -jar dist/chat-server.jar --host 0.0.0.0 --port 5555 --data server-data
# Ba terminal khác (mỗi terminal một lệnh; điền Alice, Bob, Charlie trong GUI)
java -jar dist/chat-client.jar
```

Copy cả `dist` sang laptop B, dùng IP LAN laptop A. Hướng dẫn chi tiết và xử lý
firewall ở [DEMO_GUIDE.md](DEMO_GUIDE.md).
