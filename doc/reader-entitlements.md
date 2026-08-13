# Vé đọc và quyền đọc chương

## Phạm vi đã triển khai

Vé đọc là quyền sử dụng nội bộ, tách hoàn toàn khỏi Xu và Ngọn Đuốc. Một Vé đọc mở vĩnh viễn
một chương cho một độc giả. Việc mở chương tạo `chapter_entitlement`; không tạo
`user_buy_record`, không chia doanh thu tác giả và không phát sự kiện mua chương.

Nền dữ liệu nằm trong `doc/sql/20260730_reader_entitlements.sql`:

- `reading_ticket_account`: projection số dư theo người dùng;
- `reading_ticket_lot`: lot có thời hạn, tiêu theo FIFO ưu tiên lot hết hạn sớm;
- `reading_ticket_ledger` và `reading_ticket_lot_allocation`: audit bất biến;
- `chapter_entitlement`: quyền đọc idempotent, tối đa một bản ghi `ACTIVE` cho mỗi người/chương.

Nền thuê bao nằm trong `doc/sql/20260731_reader_subscriptions.sql`:

- `reading_subscription_plan`: catalog gói, không seed giá hay gói thương mại;
- `user_reading_subscription`: snapshot quyền lợi, tối đa một thuê bao `ACTIVE`/`PAUSED` mỗi user;
- `reading_subscription_period_grant`: biên nhận cấp theo kỳ bất biến, liên kết một-một với ledger Vé đọc.

Activation hiện chỉ dành cho luồng admin/internal. Core service snapshot số Vé, độ dài kỳ và thời hạn
Vé từ plan `ACTIVE`; scheduler lấy tối đa một batch thuê bao đến hạn mỗi lần chạy rồi gọi service
transactional. Một lỗi chỉ làm thất bại thuê bao tương ứng, không chặn các phần tử còn lại trong
batch. Hai worker cùng cấp một kỳ chỉ tạo một ledger, một lot và một biên nhận.

API hiện có:

- `GET /user/reading-tickets`: số dư Vé đọc của người dùng đăng nhập;
- `POST /book/{bookId}/chapter/{bookIndexId}/reading-ticket-unlock`: mở chương bằng Vé đọc;
- body mở chương: `{ "clientRequestId": "request_0001" }`.
- `GET /user/reading-subscriptions/plans`: danh sách plan `ACTIVE`;
- `GET /user/reading-subscriptions/current`: thuê bao đang mở của người dùng đăng nhập;
- `GET /user/reading-subscriptions/{subscriptionId}/period-grants?limit=50`: lịch sử cấp kỳ có
  kiểm tra ownership tại truy vấn database.
- `POST /user/reading-subscriptions/checkouts`: tạo đơn mua một kỳ bằng VNPAY hoặc VietQR; body
  chỉ nhận `planCode`, `payChannel` và `clientRequestId`, còn user/giá/quyền lợi được lấy lại và
  snapshot ở server.

Giao diện độc giả hiện có:

- `/user/reading_tickets.html` trên desktop/mobile: hiển thị số dư, catalog gói đang mở, thuê bao
  hiện tại và lịch sử cấp Vé theo kỳ bằng các API session-owned ở trên;
- catalog và trang admin đã có `price_vnd` theo kỳ; migration không seed giá và các plan cũ không
  có giá không được tự động mở bán;
- plan có giá và kênh đang cấu hình hiển thị nút VNPAY/VietQR. Retry mất kết nối giữ nguyên
  `clientRequestId`; VietQR chỉ hiển thị QR, không tự kích hoạt trước webhook ngân hàng;
- panel chương VIP có nút **Mở bằng 1 Vé đọc**; client tạo `clientRequestId` và giữ nguyên mã này
  khi mất kết nối để retry không tiêu Vé lần hai;
- khi feature flag tắt, nút mở chương không được hiện; khách chưa đăng nhập được chuyển về trang
  đăng nhập kèm `originUrl`;
- giao diện nằm trong runtime base, còn các theme chỉ tham chiếu fragment/asset chung. `green`,
  `orange`, `dark` đã được nối vào template trang đọc ghi đè; `blue` dùng fallback runtime.

Mã quà `READING_TICKET` đã dùng lại `ReadingTicketService.grant()` và tạo lot nguồn `GIFT_CODE`;
chi tiết campaign, HMAC và rollout nằm tại `doc/gift-codes.md`.

## Xử lý đơn thuê bao đã thanh toán

Nếu webhook xác nhận tiền nhưng người dùng đang có thuê bao `ACTIVE/PAUSED`, đơn mua chuyển sang
`PAID_REVIEW` thay vì cộng Xu hoặc ghi đè quyền hiện hữu. Admin có quyền
`novel:readingSubscription:review` xem hàng đợi và thực hiện một trong hai thao tác:

- thử kích hoạt lại từ đúng snapshot giá/quyền lợi/thời điểm settlement; nếu vẫn còn thuê bao mở,
  đơn giữ nguyên `PAID_REVIEW` và ghi audit `RETRY_BLOCKED`;
- chuyển sang `REFUND_PENDING`, chỉ ghi trạng thái và audit `REFUND_REQUESTED`. Thao tác này không
  tự gọi ngân hàng, không cộng/trừ ví và không đánh dấu đã hoàn tiền.

Hai thao tác khóa hàng purchase, kiểm tra optimistic version và lấy `operatorId` từ session admin.
Audit không cho UPDATE/DELETE. Hai admin retry đồng thời chỉ một transaction có thể tạo thuê bao và
chuyển đơn sang `ACTIVATED`; request còn lại bị từ chối vì version/status đã đổi.

Các endpoint thuê bao yêu cầu đăng nhập và không trả `userId`, nguồn kích hoạt, `sourceRef` hoặc
policy nội bộ. Checkout không trực tiếp kích hoạt: chỉ callback đã xác thực hoặc QueryDr mới được
chốt `order_pay` và dispatch theo purchase snapshot.

`reading_subscription_purchase` giữ snapshot giá/quyền lợi và bốn trạng thái:

- `PENDING`: chờ cổng thanh toán;
- `ACTIVATED`: đã thanh toán và tạo thuê bao;
- `FAILED`: cổng xác nhận thất bại hoặc khởi tạo thanh toán thất bại;
- `PAID_REVIEW`: tiền đã được xác nhận nhưng user đã có thuê bao mở; không cộng Xu và không tạo
  thuê bao thứ hai, cần vận hành xử lý.

Mỗi user chỉ có tối đa một purchase `PENDING`/`PAID_REVIEW`. Callback lặp không kích hoạt lần hai;
đơn thuê bao dùng `account_amount=0`, nên không đi qua `creditReaderTopUp()`.

Admin có trang `/novel/readingSubscription` với ba quyền tách biệt:

- `novel:readingSubscription:view`: xem plan và thuê bao đang mở;
- `novel:readingSubscription:config`: tạo/sửa/kích hoạt/ngừng plan bằng optimistic version;
- `novel:readingSubscription:activate`: activation thủ công có audit và idempotency.

Plan không bị xóa vật lý; `RETIRED` là trạng thái kết thúc. Activation thủ công còn bị khóa bởi
`READING_SUBSCRIPTION_ADMIN_ACTIVATION_ENABLED=false` ngoài permission.

Endpoint chỉ nhận mã request từ client. `userId`, quan hệ tác phẩm/chương, trạng thái kiểm duyệt,
giới hạn tuổi, giá và chính sách thương mại đều được lấy lại ở server. Retry cùng request không
tiêu vé lần hai; hai request đồng thời cho cùng chương cũng chỉ tạo một entitlement và một bút
toán `SPEND`.

## Cấu hình và rollout

Tính năng mặc định tắt:

```dotenv
READING_TICKET_ENABLED=false
READING_TICKET_POLICY_VERSION=v1
READING_TICKET_MAX_LOTS_PER_SPEND=20
READING_TICKET_EXPIRY_CRON=0 30 3 * * ?
READING_TICKET_EXPIRY_BATCH_SIZE=500
READING_TICKET_EXPIRY_MAX_LOTS_PER_USER=1000
READING_SUBSCRIPTION_GRANT_CRON=0 10 0 * * ?
READING_SUBSCRIPTION_ZONE_ID=Asia/Ho_Chi_Minh
READING_SUBSCRIPTION_GRANT_BATCH_SIZE=200
READING_SUBSCRIPTION_ADMIN_ACTIVATION_ENABLED=false
```

`READING_TICKET_ENABLED` khóa cả API Vé đọc, job đóng lot hết hạn và job cấp Vé theo kỳ. Biên kỳ
được tính theo tháng lịch trong `READING_SUBSCRIPTION_ZONE_ID`; thời điểm nghiệp vụ được truyền từ
Java, không phụ thuộc timezone của MySQL hoặc hệ điều hành.

Trình tự bật:

1. Sao lưu database, chạy lần lượt migration `20260730` và `20260731`, rồi chạy lại lần hai để
   xác minh idempotent.
2. Chạy MySQL integration/concurrency test trên staging.
3. Deploy ứng dụng với cờ vẫn tắt và kiểm tra health/log.
4. Chỉ tạo plan/activation staging bằng nguồn admin/internal đã kiểm soát; chưa bán thuê bao.
5. Bật `READING_TICKET_ENABLED=true` trên một instance/canary.
6. Kiểm tra số dư, mở chương, retry, cấp kỳ, chương đã mua, chương miễn phí và chương giới hạn tuổi.
7. Mở dần lưu lượng sau khi đối chiếu account với tổng lot còn hiệu lực và biên nhận cấp kỳ.

Rollback bằng cách tắt cờ, không xóa bảng hay entitlement đã phát sinh. Quyền đọc đã cấp vẫn được
luồng render/reader-state nhận diện để không thu lại quyền của độc giả; cờ chỉ khóa API xem/tiêu
Vé đọc mới.

## Phần chưa triển khai

- màn quản trị và quy trình hoàn tiền/giải quyết purchase `PAID_REVIEW`;
- smoke với credential VNPAY/VietQR production đã được phê duyệt;
- lịch sử đầy đủ từng lot và bút toán Vé đọc cho độc giả; trang hiện chỉ hiển thị số dư và lịch sử
  cấp Vé theo kỳ;
- nghiệp vụ hoàn/thu hồi Vé đọc.

Các phần trên phải dùng `ReadingTicketService.grant()` và sổ cái hiện tại, không cập nhật trực tiếp
`available_balance` và không tái sử dụng bảng Ngọn Đuốc.
