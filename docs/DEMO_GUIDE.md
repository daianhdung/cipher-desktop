# Hướng dẫn build và demo

## Chuẩn bị

JDK 21 (`java -version`, `javac -version`) và Python 3.9+ để build. Không cần IDE,
Docker hay cài SQLite Server. Build lần đầu cần internet nếu thiếu JAR trong lib.

```bash
cd /Users/daianhdung/Documents/PTIT/HKVII_Lap-trinh-mang/DoAn_ChatAES
python3 scripts/build.py
python3 scripts/test.py
```

Trên máy khác, thay đường dẫn `cd` bằng nơi copy project. Build tạo:
`dist/chat-server.jar`, `dist/chat-client.jar`, `dist/lib/`. `DoAn_ChatAES.jar` là alias
Client mới. Copy **cả thư mục dist**, không chỉ một file JAR, sang máy demo.
Sau khi build chỉ cần Java 21 để chạy, không cần Python trên laptop thứ hai.

## Một laptop, ba GUI riêng

Terminal 1 (working directory DoAn_ChatAES):

```bash
java -jar dist/chat-server.jar --host 127.0.0.1 --port 5555 --data server-data
```

Mở ba terminal khác, mỗi terminal chạy:

```bash
java -jar dist/chat-client.jar
```

Nhập lần lượt Alice, Bob, Charlie; Server `localhost`, cổng `5555`; bấm Kết nối.
Các GUI là ba process Java độc lập. Chọn người nhận ở cột trái trước khi gửi.
Online list cập nhật theo phiên TCP; các tên từng gặp còn hiển thị Offline để mở lịch sử.
Trong terminal Server gõ `status`, `sessions` để xem số lượng và UUID từng phiên;
gõ `quit` hoặc Ctrl+C để tắt. Không khởi động hai Server trên cùng --data.

Kịch bản: Alice → Bob tiếng Việt, Bob trả lời; Charlie không nhận tin riêng.
Chọn Encryption Lab: nhập khóa và bản rõ → Xem bản mã / Mã hóa và phân tích.
Nút Phân tích áp dụng khóa vừa nhập; khóa để trống giữ khóa phiên hiện tại.
Với mỗi text chat Server cũng đếm, kết quả được xem trong Lab.

Chọn Bob → Gửi file → chọn file mẫu `data/mau/anh_mau.png`, audio/video hoặc binary.
Server nhận đủ trước rồi chuyển đến Bob; Bob thấy tiến độ, file hoàn chỉnh được lưu
ở `client-downloads/Bob/<UUID>_<name>`. Chọn attachment → Lưu bản sao để chọn nơi lưu.
Sender thấy xác nhận sau khi Bob kiểm SHA-256. Hủy upload không đóng các phiên khác.
Tệp tối đa 1 GiB, văn bản tối đa 8192 byte UTF-8.

Restart Server với cùng --data. Alice mở lại cuộc trò chuyện Bob: metadata, thống kê,
attachment vẫn còn; text cũ không giải mã được sau restart (khóa không lưu xuống đĩa).
Bấm Tải file đã chọn để lấy attachment cũ, dù người gửi đang offline.

## Hai laptop cùng Wi-Fi

**Laptop A:** copy dist. Terminal A1 chạy Server:

```bash
java -jar dist/chat-server.jar --host 0.0.0.0 --port 5555 --data server-data
```

Terminal A2 chạy GUI Alice:

```bash
java -jar dist/chat-client.jar
```

Alice dùng `localhost:5555`. Xem IPv4 Wi-Fi của A trong phần Network của hệ điều hành
(macOS thường `ipconfig getifaddr en0`; Windows `ipconfig`; Linux `ip -4 addr`).
Ví dụ A có IP `192.168.1.10` — phải thay bằng IP thực, không hardcode ví dụ.

**Laptop B:** copy cả dist, mở hai terminal và chạy hai GUI Bob, Charlie:

```bash
java -jar dist/chat-client.jar
java -jar dist/chat-client.jar
```

Cả Bob và Charlie nhập IP LAN **của laptop A**, cổng `5555`. Không nhập `localhost`
trên B vì nó trỏ về chính B. `0.0.0.0` là địa chỉ bind Server, không dùng làm đích Client.
Hai laptop dùng cùng mạng riêng, không guest Wi-Fi có client/AP isolation.
Cho phép Java nhận kết nối TCP inbound port 5555 trong firewall của A (private network).
Không cần mở port router hay đưa Server ra internet.
Đối chiếu fingerprint RSA trong tab nhật ký Client với terminal A trước khi gửi dữ liệu.

Trình tự demo: ba tên online → Alice/Bob chat riêng → Charlie kiểm không nhận →
Bob/Charlie gửi đồng thời → chuyển media hai chiều → Lab đếm chữ → DB → ngắt/restart.
LAN trên hai máy thật phải được thử riêng; hiện chưa được xác nhận bằng kiểm thử tự động.

## Lỗi thường gặp

| Dấu hiệu | Xử lý |
|---|---|
| Connection refused | Kiểm tra Server đang RUNNING, IP và port, không nhầm localhost trên laptop B |
| Timeout | Firewall A, Wi-Fi khác subnet/guest isolation, A ngủ, VPN; thử đổi mạng riêng |
| Address already in use | Tắt Server cũ hoặc đổi cả port Server và Client |
| Username đang online | Đổi tên hoặc đóng phiên cũ; sau mất mạng có thể đợi timeout 120 giây |
| ClassNotFound org.sqlite.JDBC | Copy đủ dist/lib cạnh JAR; build lại bằng scripts/build.py |
| SQLite busy / CSDL lỗi | Không chạy hai Server trên cùng data; đóng công cụ giữ write transaction, thử lại |
| Bản mã lịch sử không có khóa | Giới hạn đã thiết kế; metadata/thống kê/file vẫn truy cập được |
| File interruption | Gửi lại; không có resume; .part không được đưa vào danh sách file hoàn chỉnh |
| Không gửi được offline | V1 extension không có offline queue; chờ online, hoặc tải attachment cũ từ lịch sử |

## Bản cũ để đối chiếu môn học

Dùng port riêng nếu Server mới vẫn chạy:

```bash
java -cp build/classes server.MockServer 5556
java -jar dist/chat-client.jar --legacy
```

Trong GUI cũ nhập cổng 5556. Bản cũ có AES-CBC, upload lên Server và thumbnail;
không có multi-user/CSDL, chỉ dùng so sánh/regression. Không kết nối v1 GUI với v2 Server.
