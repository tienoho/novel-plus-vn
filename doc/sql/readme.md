# Hướng dẫn SQL

1. `novel_plus.sql` chứa cấu trúc cơ sở dữ liệu và dữ liệu mặc định, không bao gồm toàn bộ nội dung truyện mẫu.
2. Các tệp có tên `yyyyMMdd.sql` là migration tăng dần theo ngày phát hành.
3. Khi cài đặt mới, chạy `novel_plus.sql`, sau đó chạy các migration mới hơn theo thứ tự thời gian.
4. Khi nâng cấp, xác định ngày của phiên bản đang dùng và chỉ chạy các migration xuất hiện sau ngày đó.
5. Không sửa migration lịch sử đã được phát hành. Mọi thay đổi dữ liệu mới phải nằm trong một migration mới và có thể chạy lại an toàn.
6. `20260712_vi_localization.sql` Việt hóa dữ liệu mặc định bằng điều kiện khớp chính xác, không ghi đè dữ liệu đã được quản trị viên tùy chỉnh.
7. `20260716_vnpay_hardening.sql` lưu số Xu đã cam kết theo đơn, tạo unique index cho `out_trade_no` và index đối soát đơn chờ. Migration sẽ dừng nếu dữ liệu cũ có mã đơn trùng để bắt buộc đối soát thủ công trước khi tiếp tục.
8. `20260717_wallet_ledger.sql` tạo ví độc giả, ví doanh thu tác giả và sổ cái kép bất biến. Migration khởi tạo số dư ví độc giả từ `user.account_balance` đúng một lần, đồng thời ghi giao dịch số dư đầu kỳ cân bằng với tài khoản phát hành Xu của hệ thống.
9. `20260718_author_payout.sql` tạo hồ sơ KYC tác giả, yêu cầu rút thu nhập và audit trail bất biến. Các trường CCCD/hộ chiếu, mã số thuế và tài khoản ngân hàng chỉ nhận ciphertext từ ứng dụng; migration không chứa hoặc tự sinh dữ liệu định danh.
10. `20260720_refund_reconciliation_vietqr.sql` tạo state machine refund/chargeback, clearing hoàn tiền, audit bất biến, cấu hình kênh thanh toán và bảng đối soát ngân hàng.
11. `20260725_moderation_copyright.sql` tạo dữ liệu kiểm duyệt, báo cáo bản quyền, bằng chứng sở hữu và các cột phân loại độ tuổi. Mọi `ALTER TABLE` kiểm tra `information_schema` trước khi chạy để migration có thể lặp lại.
12. `20260725_reports_security.sql` tạo báo cáo doanh thu/chứng từ kỹ thuật, audit bảo mật, 2FA và menu quản trị tương ứng. Migration không bật phát hành chứng từ hoặc tự điền pháp nhân/mã số thuế.
13. `20260726_author_editor.sql` tạo bản nháp chương riêng tư, optimistic version, lịch xuất bản và audit trạng thái bất biến.
14. `20260726_simhash_storage.sql` đồng bộ `book_index.sim_hash` thành chuỗi nhị phân 64 ký tự; giá trị số cũ được chuyển sang đúng biểu diễn bit và migration có thể chạy lặp.
15. `20260726_cover_moderation.sql` thêm trạng thái/lý do kiểm duyệt bìa và index hàng đợi. Bìa legacy được coi là đã duyệt; ứng dụng đặt bìa mới hoặc được thay lại về trạng thái chờ duyệt.
16. `20260726_author_analytics.sql` tạo event đọc chương ẩn danh, unique idempotency và trigger append-only phục vụ completion/retention theo chương.
17. `20260726_vietnamese_search.sql` thêm cột tìm kiếm sinh tự động với collation bỏ qua dấu và FULLTEXT ngram. Migration không sửa tên truyện/tác giả nguồn nhưng có thể rebuild bảng `book` khi tạo index.
18. `20260726_chapter_notifications.sql` tạo quan hệ theo dõi tác giả, outbox chương lần đầu được duyệt và hộp thông báo. Migration không backfill chương cũ để tránh gửi hàng loạt khi nâng cấp; unique key bảo đảm một chương chỉ tạo một event và mỗi độc giả chỉ nhận một thông báo cho chương đó.
19. `20260727_recommendation.sql` thêm index pool tác phẩm đã duyệt và index lịch sử mua theo tài khoản/tác phẩm. Migration chỉ tạo index còn thiếu, không sửa dữ liệu hành vi hoặc nội dung.
20. `20260727_author_story_bible.sql` tạo kho tư liệu riêng tư theo tác phẩm cho dàn ý, nhân vật, địa điểm và dòng thời gian. Mọi thao tác runtime phải lọc đồng thời `author_id` và `book_id`; cột `version` chống ghi đè giữa nhiều tab.
21. `20260727_author_collaboration.sql` tạo vai trò đồng tác giả/biên tập viên, sáu quyền theo tác phẩm, optimistic version và audit thay đổi quyền bất biến. Chủ sở hữu tác phẩm có toàn quyền ngầm định; migration không cấp quyền tài chính, KYC, bản quyền hoặc quản trị cộng tác viên.

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

Migration tìm kiếm tiếng Việt phải được đo trên bản sao production trước khi nâng cấp. Với bảng `book` lớn, dành cửa sổ bảo trì đủ để tạo hai cột stored và FULLTEXT index; không hủy migration giữa lúc `ALTER TABLE` đang chạy. MySQL phải dùng `ngram_token_size=2`; nếu thay đổi cấu hình này phải rebuild index và chạy lại integration tìm kiếm.

Luôn thử migration trên một bản sao dữ liệu production trước. Không dùng `docker compose down -v` trong quy trình nâng cấp vì lệnh này xóa volume MySQL.
