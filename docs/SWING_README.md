# Đề tài 8 — Java TCP AES Chat

Bản multi-user mở rộng từ project Swing/AES hiện có. Java 21, TCP Socket, AES-GCM,
RSA-OAEP, SQLite JDBC. Server giải mã để đếm chữ cái: **không phải E2EE**.
Username không phải xác thực tài khoản.

```bash
python3 scripts/build.py
java -jar dist/chat-server.jar --host 0.0.0.0 --port 5555 --data server-data
# Terminal khác: chạy 3 lần, nhập tên Alice/Bob/Charlie
java -jar dist/chat-client.jar
```

Giữ `dist/lib` cạnh JAR khi copy. Client cho nhập Server IP/cổng; laptop khác dùng IP
LAN của máy Server. File nhận ở `client-downloads/<username>`. DB ở `server-data/chat.db`.

```bash
python3 scripts/test.py
# Có desktop: test Swing thật
java -Xmx128m -cp "build/classes:build/test-v2:lib/*" ChatGuiIntegration build/chat-gui.png
```

Windows dùng `;` thay `:` trong classpath. NetBeans vẫn mở project được; main Client
là `client.ClientMain`, Server mới là `chat.server.ServerMain`.
Bản cũ: `java -jar dist/chat-client.jar --legacy`, đi cùng `server.MockServer` trên port riêng.

- [Hướng dẫn demo 1/2 laptop](DEMO_GUIDE.md)
- [Trạng thái và giới hạn đã kiểm chứng](IMPLEMENTATION_STATUS.md)
- [Yêu cầu / audit](REQUIREMENTS.md)
- [Kiến trúc](ARCHITECTURE.md)
- [Giao thức và audit mã hóa](PROTOCOL.md)
- [CSDL và giới hạn lịch sử/khóa](DATABASE.md)
- [Sequence diagrams](SEQUENCE_DIAGRAMS.md)
- [Test plan](TEST_PLAN.md)

Chat text cũ sau Server restart vẫn có metadata/thống kê, nhưng không thể giải mã
vì session key không lưu xuống đĩa. File Server lưu bản rõ để tải lại sau restart.
Chỉ dùng demo trên LAN tin cậy; không có username authentication hay chứng chỉ Server.
