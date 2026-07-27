# Original User Request

## 2026-07-25T03:59:57Z

Hoàn thiện toàn bộ 100% các hạng mục kỹ thuật P0 cho hệ thống Novel-Plus (tài chính/payout, adapter thanh toán VietQR/NAPAS, kiểm duyệt nội dung, bản quyền, bảo mật & hardening).

Working directory: d:\Project\novel-plus
Integrity mode: development

## Requirements

### R1. Payout, Ledger & Financial Workflow
- Khép kín runtime payout và migration sổ cái kép.
- Triển khai hoàn chỉnh workflow refund, chargeback và đối soát ngân hàng/ledger.
- Xử lý chính xác tự động chuyển khoản cho tác giả và tất toán tài chính.

### R2. Payment Adapter & VietQR/NAPAS Integration
- Xây dựng payment adapter dùng chung cho các cổng thanh toán.
- Triển khai giao thức VietQR/NAPAS thực tế phục vụ nạp/rút tiền.

### R3. Moderation, Content Filtering & Age Rating
- Xây dựng hàng đợi kiểm duyệt truyện, ảnh và bình luận.
- Thiết lập phân loại nội dung, giới hạn tuổi và cơ chế chống spam/truyện trùng/sao chép.

### R4. Copyright & Violation Management
- Xây dựng hệ thống tiếp nhận báo cáo vi phạm bản quyền, gỡ nội dung và xử lý kháng nghị.
- Lưu trữ lịch sử chỉnh sửa và bằng chứng sở hữu nội dung.

### R5. Reports, Tax & Receipts
- Xây dựng hệ thống báo cáo doanh thu, tính thuế và xuất chứng từ tài chính.

### R6. Security, Hardening & Quality Assurance
- Tích hợp Audit log, 2FA, rate limiting, monitoring và backup.
- Thực hiện regression testing, kiểm thử tải lớn (load test) và hardening hệ thống.

## Acceptance Criteria

### Financial & Payout
- [ ] Runtime payout chạy khép kín 100% không lệch sổ cái kép.
- [ ] Luồng refund, chargeback và đối soát ngân hàng có kết quả khớp với dữ liệu giao dịch.

### Payment Integration
- [ ] Adapter thanh toán xử lý đúng chuẩn VietQR / NAPAS cho luồng thực tế.

### Moderation & Copyright
- [ ] Hàng duyệt tự động/thủ công phân loại đúng vi phạm, độ tuổi và lọc trùng lặp nội dung.
- [ ] Quy trình gỡ bài, nộp kháng nghị và truy xuất bằng chứng bản quyền hoạt động chính xác.

### Compliance & Security
- [ ] Xuất báo cáo thuế và chứng từ doanh thu đúng định dạng.
- [ ] Đạt yêu cầu test tải lớn, bảo mật 2FA/rate limit và khôi phục sự cố monitoring/backup.

## Follow-up — 2026-07-26T01:12:08Z

Tiếp tục quy trình Teamwork hoàn thiện P0 Novel-Plus.

Tất cả 6 modules M1-M6 đã được triển khai và xác minh bởi các Worker. Đã ghi nhận trong d:\Project\novel-plus\.agents\orchestrator\progress.md.

Hãy tiếp tục và hoàn tất giai đoạn Verification / Audit (Reviewers, Challengers, Forensic Auditor) và tổng kết kết quả hoàn thành P0.

Working directory: d:\Project\novel-plus
Integrity mode: development

