# Kiểm thử AES Chat v2

Chạy từ `DoAn_ChatAES`, có JDK 21 và Python 3.9+:

```bash
python3 scripts/test.py
```

Script build JAR, compile mọi test, chạy với dữ liệu tạm và port ngẫu nhiên, dọn sau
khi xong. `ChatIntegration`/`ChatFaults` chạy heap 128 MiB; file không được nạp cả vào RAM.
Kết quả thực tế ngày 2026-11-01 được ghi trong IMPLEMENTATION_STATUS.md.

| Nhóm | Test / nội dung |
|---|---|
| Legacy crypto | `common.CommonSelfTest`: CBC, sai khóa, IV, kích thước/padding, stream boundary, RSA, tiếng Việt |
| Legacy network | `E2E`: upload 3 media, đổi khóa, text liên tiếp, ngắt; script đối chiếu SHA-256 |
| Legacy shutdown | `E2E2`: Server tắt, reset trạng thái, connection refused |
| Multi-user | `ChatIntegration`: Alice/Bob/Charlie, online, tên trùng, private chat/reply, gửi đồng thời |
| GCM | Roundtrip Unicode, tag bị sửa, nonce mới; session key rotation cả khi đang upload |
| File | 32 MiB trên heap 128 MiB, zero byte, SHA-256/byte comparison, cancel, Charlie không nhận |
| Phân quyền | FILE_GET của người thứ ba bị từ chối; sender/recipient tải từ lịch sử |
| Persistence | SQLite message/statistics, lịch sử trong lần chạy, restart giữ dữ liệu/file, thiếu khóa rõ ràng |
| Faults | `ChatFaults`: metadata >1 GiB, chunk sai thứ tự, sai GCM key, replay, truncated handshake |
| JDBC lỗi | Giữ transaction ghi ngoài để ép SQLITE_BUSY, nhận ERROR, rollback rồi gửi lại thành công |
| Isolation | Các phiên khác vẫn chat sau disconnect/malformed input; shutdown báo ngắt, Server unavailable |
| Processes | `scripts/process_test.py`: JAR Server + 3 JVM Client độc lập, chat/reply, ảnh/audio/video hash khớp |

## Swing tự động (cần desktop)

```bash
java -Xmx128m -cp "build/classes:build/test-v2:lib/*" ChatGuiIntegration build/chat-gui.png
```

Windows đổi dấu `:` trong classpath thành `;`. Test mở hai JFrame thật, thao tác nút
connect/send, xác nhận text xuất hiện, chạy Swing Timer, upload 256 MiB + đổi khóa
rồi bấm Ngắt. Kiểm tra tick EDT tiếp tục và handler ngắt <500 ms. Không thay thế
kiểm tra bằng người dùng trên các hệ điều hành khác. Ảnh chụp bằng `printAll`,
không dùng quyền screen recording. Test có thể chạy trong working directory tạm.

## Thủ công, chưa được tuyên bố PASS trên hai laptop

1. Mở ba GUI, Alice/Bob/Charlie, chọn từng peer; xác nhận label online/offline.
2. Alice gửi tiếng Việt cho Bob, Charlie không thấy nội dung; Bob trả lời.
3. Lab: nhập khóa và bản rõ, xem Base64, phân tích; đối chiếu bảng chữ cái.
4. Gửi ảnh/audio/video/binary: chọn file, tiến độ, SHA-256, recipient Lưu bản sao.
5. Hủy upload, rút Wi-Fi giữa upload/download, đóng Client, xác nhận .part không công bố.
6. Restart Server cùng --data: lịch sử metadata/thống kê/file còn; nội dung chat cũ
   hiện cảnh báo không có khóa. Chọn attachment và tải lại, kiểm hash.
7. Chạy theo DEMO_GUIDE.md trên **hai laptop thật** cùng Wi-Fi. Ghi lại IP, firewall,
   OS/JDK và kết quả; AP guest isolation phải được tắt hoặc đổi mạng.
8. Thử Save As/overwrite/cancel và bố cục tại font/scale của máy demo.

Không báo LAN PASS chỉ vì bind 0.0.0.0 hoặc localhost test thành công.
