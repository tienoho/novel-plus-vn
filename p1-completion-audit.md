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
| Vị trí đọc, bookmark, note | Ownership, version conflict, VIP/tuổi/kiểm duyệt | Reader tools dùng chung bốn theme | `doc/reader-state.md`, `20260727_reader_annotations.sql`, E2E chương dài | Đạt local/Compose |
| Font, giãn dòng, phím tắt, accessibility | Không cần schema mới | TTS, font, theme, focus, ARIA, reduced motion | `doc/reader-pwa.md`, `reader-tools.js`, E2E desktop/mobile | Đạt local/Compose |
| Gói thuê bao | Giá/quyền lợi snapshot, settlement idempotent, mandate VNPAY, renewal cycle/attempt, primary/fallback, QueryDr có optimistic lease, retry/grace, price consent và audit bất biến | Độc giả mua gói, chọn auto-renew/nguồn tiền, đổi thiết lập, chấp nhận giá hoặc hủy cuối kỳ; admin có queue renewal | Migration `20260804`–`20260816`, unit contract QueryDr, test checkout/renewal/concurrency MySQL và `doc/production-readiness-acceptance.md` | Backend/UI/MySQL local đạt; UAT one-off chờ domain/IPN, Recurring chờ credential riêng |
| Vé đọc mở chương | Account/lot FIFO/ledger/allocation bất biến, entitlement chương, expiry job và API số dư/mở chương | Có trang số dư desktop/mobile và nút mở chương idempotent trên runtime, green, orange, dark; blue dùng fallback | `doc/reader-entitlements.md`, `20260730_reader_entitlements.sql`, MySQL/concurrency và E2E đăng nhập | Đạt local/Compose |
| Mã quà tặng | Campaign/code HMAC/redemption bất biến; key ring active + khóa xác minh cũ; reward Xu/Vé đọc transactional; lịch sử session-owned; revoke optimistic; join-lock campaign chặn vượt `max_per_user` khi cùng user đổi hai code đồng thời | UI phát hành/tra cứu/thu hồi admin, đổi mã và lịch sử desktop/mobile | `doc/gift-codes.md`, migration `20260802`–`20260803` và `20260806`, concurrency và E2E đăng nhập | Đạt local/Compose |

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
- [x] Chạy browser đã đăng nhập cho đổi mã, Vé đọc, mua thuê bao, hàng đợi admin và reader-state/chương dài; CAPTCHA không bị tắt hoặc vượt qua trong smoke tự động — 2026-07-31, MySQL 8.4 + Redis thật qua Docker, đăng nhập reader/admin giải captcha thật qua ảnh (không tắt/bypass). Đổi mã: redeem thật ghi qua wallet ledger (`wallet_account`/`wallet_entry`), replay đúng ngữ nghĩa trả `ALREADY_REDEEMED` không cộng trùng, lịch sử che mã chỉ còn 4 ký tự cuối. Vé đọc: admin activate thuê bao → scheduler cấp 20 Vé thật, mở chương VIP bằng 1 Vé, replay `clientRequestId` trả `ALREADY_ENTITLED` không trừ lần hai, nội dung chương hiển thị đúng qua trang đọc thật sau khi mở. Mua thuê bao: checkout VietQR dựng đơn thật, QR EMV đúng chuẩn, webhook có chữ ký secret xác nhận kích hoạt thành công. Hàng đợi admin: dựng lại đúng race "đã có ACTIVE khi webhook về tiền" → đơn tự chuyển `PAID_REVIEW` không cộng nhầm; `retry` khi vẫn còn thuê bao mở trả `BLOCKED_BY_OPEN_SUBSCRIPTION` và giữ nguyên trạng thái; `refund` chuyển `REFUND_PENDING`, không tự hoàn tiền/sửa ví; cả hai đường đều ghi `reading_subscription_purchase_review_audit` bất biến. Reader-state: lưu tiến độ + note trên chính chương VIP vừa mở, đọc lại đúng cả hai. Không phát sinh lỗi 500 ngoài dự kiến trong log server.
  **Ghi nhận lịch sử đã được thay thế:** hành vi thuê bao một kỳ, không tự gia hạn nêu trong lượt smoke ngày 31/07/2026 chỉ mô tả code trước migration recurring. Hiện hệ thống đã có mandate VNPAY, gia hạn bằng ví Xu, nguồn chính/fallback do người dùng chọn, retry/grace period và price consent. Bằng chứng local không thay thế smoke VNPAY Recurring production.
- [x] Regression kỹ thuật ngày 02/08/2026 đạt: full Maven reactor JDK 21; Flyway 36 migration; MySQL concurrency; E2E bốn theme; i18n/CSP/JS; load 500 VU; backup/restore; Gitleaks; Trivy và SBOM. Chi tiết tại `doc/production-readiness-acceptance.md`.
- [x] Ngày 08/08/2026 hoàn tất hàng đợi thu hồi mandate VNPAY Recurring: hủy thuê bao dừng auto-renew
  và ghi `REVOKE_PENDING` nguyên tử; worker gọi `cancel_recurring` có lease/retry, coi `00/04/12` là
  trạng thái mong muốn và chỉ sau đó mới xóa token mã hóa. Admin có queue không lộ ciphertext,
  retry bắt buộc reason/version/lease và audit bất biến. Flyway 38 migration chạy hai lượt, MySQL hai
  worker chỉ một claim; queue có gauge/alert 15 phút và `promtool` xác nhận 13 rule; common 242/242,
  front 441 test và admin 57/57 không failure/error trên Java 21.
- [x] Ngày 09/08/2026 hoàn tất luồng thuê bao frontend và idempotency khởi tạo mandate: desktop/mobile
  có đủ API tạo/đọc mandate, auto-renew, nguồn chính/fallback, price consent và cancel; form redirect
  VNPAY Recurring dùng đúng ba field provider. Migration thứ 39 chạy hai lượt; 9 lớp MySQL đạt 40/40,
  `ReadingSubscriptionMySqlIntegrationTest` đạt 7/7 và targeted unit/UI/packaging đạt 31/31. Full
  reactor Temurin 21 đạt common 242, front 453, crawl 7 và admin 57 test; bổ sung CSRF cho POST mở
  chương được xác minh bằng 7/7 test filter/UI packaging.

## Điều kiện chưa thể tuyên bố hoàn tất

Phần kỹ thuật local/Compose đã đạt các gate hiện có. Trạng thái vẫn chưa phải `production-approved`
cho tới khi VNPAY one-off/Recurring, VietQR và AI (nếu bật) được smoke bằng credential đã phê duyệt;
đồng thời KYC, thuế/chứng từ, bản quyền, dữ liệu cá nhân, recurring consent, refund và payout bốn mắt
đã qua quy trình vận hành/pháp lý tại Việt Nam. Xem checklist bắt buộc trong
`doc/production-readiness-acceptance.md`.

Audit ngày 08/08/2026 bổ sung một blocker bảo mật: full-history Gitleaks còn phát hiện hai khóa riêng
merchant Alipay trong commit cũ. Source hiện tại không còn tích hợp Alipay và hai JWT mẫu tài liệu đã
được ignore bằng fingerprint chính xác, nhưng release vẫn bị chặn cho tới khi có bằng chứng khóa cũ
đã được thu hồi/rotate và phương án xử lý lịch sử được phê duyệt.

Tái xác minh ngày 09/08/2026 trên worktree hiện tại: full reactor Temurin 21 đạt common 242, front
448 (45 integration skip), crawl 8 và admin 60 test; Flyway/MySQL concurrency, smoke production-like
và E2E bốn theme đều đạt. Bốn image front/admin/crawl/migrations được quét lại bằng Trivy 0.68.2 với
policy `HIGH,CRITICAL` + `vuln,secret,misconfig`, không có phát hiện. Workflow release đã chặn Trivy
trước push và lưu report/digest, nhưng trạng thái production vẫn bị chặn bởi full-history secret cùng
các provider/pháp lý nêu trên.

Cùng worktree này, load test 500 VU đạt 52.728 request không lỗi, p95 đọc/khám phá/ghi lần lượt
121,22/22,89/27,9 ms, 5.272 thao tác ghi không lệch ledger/projection/idempotency. Backup GPG AES-256
và restore drill sau đủ 39 migration đạt checksum và phục hồi 143 bảng; cả hai runner đều tự dọn sạch
container, volume và secret tạm.
