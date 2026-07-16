# Hướng dẫn SQL

1. `novel_plus.sql` chứa cấu trúc cơ sở dữ liệu và dữ liệu mặc định, không bao gồm toàn bộ nội dung truyện mẫu.
2. Các tệp có tên `yyyyMMdd.sql` là migration tăng dần theo ngày phát hành.
3. Khi cài đặt mới, chạy `novel_plus.sql`, sau đó chạy các migration mới hơn theo thứ tự thời gian.
4. Khi nâng cấp, xác định ngày của phiên bản đang dùng và chỉ chạy các migration xuất hiện sau ngày đó.
5. Không sửa migration lịch sử đã được phát hành. Mọi thay đổi dữ liệu mới phải nằm trong một migration mới và có thể chạy lại an toàn.
6. `20260712_vi_localization.sql` Việt hóa dữ liệu mặc định bằng điều kiện khớp chính xác, không ghi đè dữ liệu đã được quản trị viên tùy chỉnh.

Luôn sao lưu cơ sở dữ liệu production và thử migration trên một bản sao trước khi triển khai.
