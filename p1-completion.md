# Hoàn tất P1 cho tác giả và độc giả

## Mục tiêu

Hoàn thành toàn bộ công cụ tác giả và trải nghiệm độc giả P1, giữ sổ cái P0 làm nguồn tiền duy nhất và không làm lộ bản thảo qua URL, log hoặc cache.

## Công việc

- [x] Tự lưu, versioning và lịch xuất bản chương → kiểm tra bằng test editor và MySQL integration.
- [x] Story bible, analytics và cộng tác theo tác phẩm → kiểm tra quyền, version conflict, số liệu và theme packaging.
- [x] Tìm kiếm tiếng Việt, recommendation, theo dõi/thông báo và reader-state → kiểm tra unit, MySQL integration và runtime smoke.
- [x] Font, giãn dòng, nền đọc, phím tắt và accessibility → bộ chọn dùng chung desktop/mobile, font phù hợp tiếng Việt, semantic button, ARIA/focus/reduced-motion, cho phép browser zoom; Chrome 320 px và bốn theme packaging đạt.
- [x] Bảo vệ AI: chuyển nội dung khỏi query string, lấy ngữ cảnh tác phẩm có giới hạn và ghi provenance không chứa bản thảo thô → kiểm tra authorization, hash audit và request body.
- [x] Cho tác giả cấu hình giá chương, thời điểm tự mở khóa và sự kiện miễn phí → dịch vụ truy cập trung tâm, giá authoritative khi khóa hàng, offline chỉ cho miễn phí vĩnh viễn; unit/MySQL integration, bốn theme và Docker smoke đều đạt.
- [x] Import/export TXT, DOCX và EPUB an toàn → quyền theo tác phẩm, import draft idempotent/transactional, export chương đã duyệt; kiểm tra zip bomb/path traversal/XXE, encoding, ngân sách tổng, thứ tự chương, DOCX styles, EPUB 3, round-trip và rollback MySQL.
- [ ] Gói thuê bao, vé đọc và mã quà tặng → kiểm tra entitlement, idempotency, hết hạn và mọi biến động Xu qua sổ cái kép.
- [ ] Chạy migration trên MySQL thật, full Maven reactor, i18n/JS verifier, bốn theme, Docker smoke và cập nhật biên bản P1.

## Hoàn tất khi

- [ ] Mọi yêu cầu P1 có implementation, migration, test lỗi/biên và tài liệu vận hành tương ứng.
- [ ] Không có nội dung bản thảo trong URL/cache/log first-party và không có đường tiền nào ghi trực tiếp số dư.
- [ ] Full regression đạt trên front desktop/mobile và bốn theme.

## Ghi chú

Các cổng AI, thanh toán và quyền tác phẩm phải fail closed. File nhập là dữ liệu không tin cậy; export chỉ cho chủ sở hữu hoặc cộng tác viên có quyền quản lý chương.
