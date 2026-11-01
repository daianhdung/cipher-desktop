# Test hiện tại (v2 + regression v1)

Chạy `python3 scripts/test.py` từ thư mục DoAn_ChatAES. Test Swing và LAN thủ công:
[xem TEST_PLAN.md](../docs/TEST_PLAN.md). Các lệnh bên dưới là test v1 cũ;
port có thể đổi bằng JVM property `-Daes.test.port=5556`.

# Kịch bản kiểm thử (không dùng JUnit)

Compile project trước (`javac -encoding UTF-8 -d build/classes $(find src -name '*.java')`), chạy `server.MockServer` ở cửa sổ khác, rồi:

```bash
javac -encoding UTF-8 -cp build/classes -d build/test test/*.java
java  -cp build/classes:build/test E2E  data/mau/anh_mau.png data/mau/audio_mau.wav data/mau/video_mau.mp4   # → E2E OK
java  -cp build/classes:build/test E2E2 <pid của MockServer>                                                # kill server giữa phiên → E2E2 OK
java  -cp build/classes:build/test ScreenshotCapture ../docs/images/doan data/mau/anh_mau.png <file lớn>              # chụp 9 ảnh cho báo cáo
```

`common.CommonSelfTest` (trong `src/`) là bộ kiểm tra đơn vị AES/RSA/đếm chữ cái: `java -cp build/classes common.CommonSelfTest`.
