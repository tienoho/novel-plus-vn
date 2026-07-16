# Hướng dẫn SQL

1. `novel_plus.sql` chứa cấu trúc cơ sở dữ liệu và dữ liệu mặc định, không bao gồm toàn bộ nội dung truyện mẫu.
2. Các tệp có tên `yyyyMMdd.sql` là migration tăng dần theo ngày phát hành.
3. Khi cài đặt mới, chạy `novel_plus.sql`, sau đó chạy các migration mới hơn theo thứ tự thời gian.
4. Khi nâng cấp, xác định ngày của phiên bản đang dùng và chỉ chạy các migration xuất hiện sau ngày đó.
5. Không sửa migration lịch sử đã được phát hành. Mọi thay đổi dữ liệu mới phải nằm trong một migration mới và có thể chạy lại an toàn.
6. `20260712_vi_localization.sql` Việt hóa dữ liệu mặc định bằng điều kiện khớp chính xác, không ghi đè dữ liệu đã được quản trị viên tùy chỉnh.
7. `20260716_vnpay_hardening.sql` lưu số Xu đã cam kết theo đơn, tạo unique index cho `out_trade_no` và index đối soát đơn chờ. Migration sẽ dừng nếu dữ liệu cũ có mã đơn trùng để bắt buộc đối soát thủ công trước khi tiếp tục.

## Nâng cấp database đang hoạt động

1. Dừng thao tác tạo đơn hoặc đưa ứng dụng vào chế độ bảo trì.
2. Sao lưu database và kiểm tra có thể phục hồi bản sao lưu.
3. Kiểm tra mã đơn trùng trước khi chạy migration VNPAY:

   ```sql
   SELECT out_trade_no, COUNT(*) AS duplicate_count
   FROM order_pay
   GROUP BY out_trade_no
   HAVING COUNT(*) > 1;
   ```

4. Nếu truy vấn trả dữ liệu, đối soát từng đơn với lịch sử cổng thanh toán. Không tự động xóa hoặc gộp đơn.
5. Chạy migration theo thứ tự thời gian, sau đó chạy lại cùng migration để xác minh tính idempotent.
6. Kiểm tra các cột/index VNPAY:

   ```sql
   SHOW COLUMNS FROM order_pay LIKE 'account_amount';
   SHOW INDEX FROM order_pay WHERE Key_name IN (
       'uk_order_pay_out_trade_no',
       'idx_order_pay_vnpay_reconcile'
   );
   ```

7. Khởi động ứng dụng và kiểm tra log của service `migrate`, `front`, `crawl` và `admin`.

Luôn thử migration trên một bản sao dữ liệu production trước. Không dùng `docker compose down -v` trong quy trình nâng cấp vì lệnh này xóa volume MySQL.
