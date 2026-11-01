# Đề tài 8: yêu cầu và kiểm kê trước nâng cấp

Bản gốc Java 21/NetBeans: `ClientFrame` → `ClientConnection` → TCP → `MockServer`.
Dùng lại `AESUtil`, `RSAUtil`, `LetterFrequency`, dữ liệu mẫu, test E2E và tài liệu cũ.
Giao thức v1 dùng AES-CBC, `writeUTF` và thân file liền mạch; không thể định tuyến
hay xen dữ liệu khi đang truyền file. Vì vậy thêm v2 có phiên, frame hữu hạn và GCM;
giữ v1 riêng để kiểm thử tương thích, không chạy hai giao thức trên cùng cổng.

| Yêu cầu | Trước nâng cấp | Cần triển khai / kiểm chứng |
|---|---|---|
| Java 21, TCP, nhập host/port | Có | Bind interface Server |
| Nhập văn bản/khóa, AES, RSA | Có | GCM, vòng đời khóa |
| Đếm chữ Việt, kết quả | Có | Lưu SQLite |
| Upload file stream | Có | Chuyển đến người nhận, SHA-256 |
| Username, online, private chat | Thiếu | Phiên riêng, chống trùng tên |
| CSDL, lịch sử | Thiếu | JDBC SQLite, ciphertext, metadata |
| GUI | Một màn hình xử lý | Chat + Encryption Lab |
| EDT không bị chặn | Một phần | Ngắt/đổi khóa/text ngoài EDT |
| Đóng gói | JAR Client cũ | JAR Server/Client độc lập |
| Ba Client, restart, LAN | Chưa test mở rộng | Test tự động; LAN thủ công |

CSDL là **bắt buộc** theo đề tài 8. Username không phải xác thực mật khẩu.
Phạm vi mở rộng: chat riêng qua Server và chuyển file; không E2EE, không tài khoản,
không queue offline, không framework/cloud. Các stage: phiên → messaging → file →
persistence → Swing → regression/đóng gói; trạng thái cuối xem IMPLEMENTATION_STATUS.md.
