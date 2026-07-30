# Audit hoàn tất P1 Novel Plus

## Mục tiêu

Theo dõi trạng thái thực tế của P1 bằng bằng chứng trong source, tách backend/frontend và không coi
test mặc định xanh là bằng chứng cho các tích hợp production hoặc tính năng chưa có.

## Ma trận hiện trạng

| Yêu cầu | Backend/DB | Frontend | Bằng chứng | Trạng thái |
|---|---|---|---|---|
| Editor, bản nháp, lịch xuất bản, lịch sử | Service, optimistic version, scheduler, migration | Editor và danh sách bản nháp tác giả | `doc/author-editor.md`, `20260726_author_editor.sql` | Đã có |
| Dàn ý, nhân vật, địa điểm, timeline | CRUD riêng tư theo tác giả–truyện | Trang story bible responsive | `doc/author-story-bible.md`, `20260727_author_story_bible.sql` | Đã có |
| Analytics chương | Event bất biến, tổng hợp lượt đọc/giữ chân/doanh thu | Dashboard tác giả | `doc/author-analytics.md`, `20260726_author_analytics.sql` | Đã có |
| Chương miễn phí/VIP, giá và mở khóa | Policy theo chương, giá authoritative, cửa sổ miễn phí | Form editor và luồng mua/đọc | `doc/chapter-commercial-policy.md`, `20260728_chapter_commercial_policy.sql` | Đã có |
| Đồng tác giả/biên tập viên | Sáu quyền theo tác phẩm và audit | Trang quản lý cộng tác viên | `doc/author-collaboration.md`, `20260727_author_collaboration.sql` | Đã có |
| Import/export TXT, DOCX, EPUB | Parser/exporter, giới hạn archive và rollback | API/form tác giả | `doc/book-import-export.md` | Đã có |
| AI theo ngữ cảnh và provenance | Hash đầu vào/đầu ra, metadata model, fail-closed | Toolbar tác giả | `doc/author-ai.md`, `20260728_author_ai.sql` | Code có; chưa smoke provider thật |
| Tìm kiếm tiếng Việt | Chuẩn hóa có/không dấu và fuzzy fallback | Search desktop/mobile | `doc/vietnamese-search.md`, `20260726_vietnamese_search.sql` | Đã có |
| Đề xuất truyện | Candidate an toàn tuổi/kiểm duyệt, loại truyện đã biết | Khối đề xuất | `doc/recommendation.md`, `20260727_recommendation.sql` | Đã có |
| Theo dõi và thông báo chương | Follow, outbox, retry và inbox | Badge/hộp thư desktop/mobile | `doc/chapter-notifications.md`, `20260726_chapter_notifications.sql` | Đã có |
| Vị trí đọc, bookmark, note | Ownership, version conflict, VIP/tuổi/kiểm duyệt | Reader tools dùng chung bốn theme | `doc/reader-state.md`, `20260727_reader_annotations.sql` | Đã có; còn browser chương dài |
| Font, giãn dòng, phím tắt, accessibility | Không cần schema mới | TTS, font, theme, focus, ARIA, reduced motion | `doc/reader-pwa.md`, `reader-tools.js` | Đã có; còn browser thực |
| Gói thuê bao | Giá/quyền lợi snapshot, settlement idempotent, `PAID_REVIEW`/`REFUND_PENDING`, audit bất biến, retry cạnh tranh an toàn, cấp kỳ transactional và QueryDr nhận đơn `account_amount=0` | Độc giả xem/mua gói qua VNPAY/VietQR; admin cấu hình plan và xử lý hàng đợi bằng quyền riêng | `20260804`–`20260805`, `ReadingSubscriptionCheckoutMySqlIntegrationTest`, `ReadingSubscriptionPaidReviewConcurrencyIT`, `reading_tickets.html` | Backend/admin và MySQL đạt; shell theme đạt browser smoke, còn luồng đăng nhập/provider production |
| Vé đọc mở chương | Account/lot FIFO/ledger/allocation bất biến, entitlement chương, expiry job và API số dư/mở chương | Có trang số dư desktop/mobile và nút mở chương idempotent trên runtime, green, orange, dark; blue dùng fallback | `doc/reader-entitlements.md`, `20260730_reader_entitlements.sql`, `ReadingTicketUiPackagingTest`, MySQL/concurrency IT | Backend và UI đã nối; còn browser thực và lịch sử toàn bộ lot/ledger |
| Mã quà tặng | Campaign/code HMAC/redemption bất biến; key ring active + khóa xác minh cũ; reward Xu/Vé đọc transactional; lịch sử session-owned; revoke optimistic; join-lock campaign chặn vượt `max_per_user` khi cùng user đổi hai code đồng thời | UI phát hành/tra cứu/thu hồi admin, đổi mã và lịch sử desktop/mobile | `doc/gift-codes.md`, migration `20260802`–`20260803` và `20260806`, `GiftCodeConcurrencyIT` 6/6 | Backend, rotation và concurrency đạt; shell theme đạt browser smoke, còn luồng đăng nhập |

## Thứ tự tiếp theo

- [x] Tách Vé đọc khỏi Xu/Ngọn Đuốc, tạo entitlement chương và nối kiểm tra quyền vào cùng luồng
  render/reader-state/mua chương; cấp kỳ thuê bao dùng lại ledger Vé đọc.
- [x] Thêm API read-only plan/thuê bao/lịch sử kỳ, không lộ nguồn kích hoạt và không có endpoint ghi.
- [x] Thêm CRUD plan và activation admin có permission/audit, optimistic version và feature flag mặc định tắt.
- [x] Chốt mã quà hỗ trợ cả Xu và Vé đọc; plaintext chỉ trả một lần, DB chỉ lưu HMAC.
- [x] Thiết kế migration bất biến/idempotent, backend transactional, quyền admin và UI đổi mã.
- [x] Hoàn tất lịch sử redemption admin/user và thu hồi code chưa dùng có permission riêng.
- [x] Hoàn tất trang tài khoản Vé đọc desktop/mobile và nút mở chương dùng chung cho bốn theme.
- [x] Thiết kế rotation HMAC: code cũ gắn `legacy-v1`, code mới dùng khóa active, tối đa bảy khóa xác minh cũ và fail closed khi key ring sai.
- [x] Hoàn tất checkout/payment dispatch thuê bao cho VNPAY/VietQR; đơn không cộng nhầm Xu và xung đột vào `PAID_REVIEW`.
- [x] Thêm màn quản trị/quy trình xử lý `PAID_REVIEW`, retry optimistic/concurrency, `REFUND_PENDING` và audit bất biến; không tự hoàn tiền hoặc sửa ví.
- [x] Smoke trang chủ desktop/mobile của green, orange, dark và blue trên artifact Docker: `lang="vi"`, không chữ Hán/raw key, không tràn ngang và không lỗi console; smoke trang đăng nhập admin công khai cũng đạt.
- [ ] Smoke provider production cho VNPAY/VietQR bằng credential được phê duyệt.
- [ ] Chạy browser đã đăng nhập cho đổi mã, Vé đọc, mua thuê bao, hàng đợi admin và reader-state/chương dài; CAPTCHA không bị tắt hoặc vượt qua trong smoke tự động.
- [x] Local regression cuối đạt: full Maven reactor 583 test, không failure/error (33 integration test có cờ riêng); Gift Code concurrency MySQL 6/6; i18n parity, 212 JS, 349 inline script và `git diff --check` đều đạt.

## Điều kiện chưa thể tuyên bố hoàn tất

P1 chỉ hoàn tất khi thuê bao có payment adapter/UI mua, mã quà và Vé đọc qua browser regression và có phương án rotation khóa; AI được smoke với credential đã
phê duyệt; reader-state/PWA được kiểm tra trên browser thật; và các cổng thanh toán, KYC, thuế, bản
quyền cùng dữ liệu cá nhân đã qua quy trình vận hành/pháp lý tại Việt Nam. Không đánh dấu goal hoàn
tất dựa trên unit test hoặc artifact local khi các điều kiện này chưa có bằng chứng.
