# Tích hợp và vận hành VNPAY

Novel Plus dùng giao thức VNPAY 2.1.0 với HMAC-SHA512. VNPAY là kênh tạo giao dịch mới duy nhất trong giao diện; mã kênh lưu tại `order_pay.pay_channel` là `4`. Dữ liệu đơn của Alipay/WeChat cũ chỉ được giữ để đối soát lịch sử và không còn endpoint tạo giao dịch mới.

## 1. Luồng xử lý

1. Người dùng đăng nhập, chọn mệnh giá trên `/pay/index.html` và gửi `POST /pay/vnpay`.
2. Backend chỉ chấp nhận mệnh giá thuộc allowlist, chốt số Xu vào `order_pay.account_amount`, tạo mã đơn duy nhất rồi chuyển hướng sang VNPAY.
3. VNPAY đưa trình duyệt về `GET /pay/vnpay/return`. Return URL chỉ kiểm tra chữ ký và trạng thái đơn để hiển thị kết quả; không cộng Xu.
4. VNPAY gọi `GET /pay/vnpay/ipn`. IPN kiểm tra checksum, mã đơn, kênh và số tiền trước khi cập nhật đơn, ghi giao dịch sổ cái kép và đồng bộ projection số dư trong cùng transaction.
5. Scheduler QueryDr đối soát đơn chờ khi IPN bị mất hoặc gián đoạn.

Callback lặp không cộng Xu lần hai. Số Xu dùng khi hoàn tất lấy từ đơn đã lưu, không tính lại theo tỷ lệ hiện tại.

Chi tiết tài khoản đối ứng, idempotency và kiểm toán nằm trong [kiến trúc ví và sổ cái](wallet-ledger.md).

## 2. Cấu hình merchant

Sao chép `.env.example` thành `.env`, sau đó cấu hình:

| Biến | Bắt buộc khi bật | Ý nghĩa |
|---|---:|---|
| `VNPAY_ENABLED` | Có | Đặt `true` để mở VNPAY; mặc định `false` |
| `VNPAY_TMN_CODE` | Có | Mã website 8 ký tự chữ/số do VNPAY cấp |
| `secrets/vnpay_hash_secret` | Có | File secret HMAC do VNPAY cấp; Compose nạp qua `VNPAY_HASH_SECRET_FILE` |
| `VNPAY_PAY_URL` | Có | Endpoint tạo thanh toán sandbox/production |
| `VNPAY_RETURN_URL` | Có | URL HTTPS công khai trả trình duyệt về website |
| `VNPAY_QUERY_URL` | Khi bật QueryDr | API truy vấn giao dịch sandbox/production |
| `VNPAY_XU_PER_1000_VND` | Có | Số Xu nhận cho mỗi 1.000 VND; phải lớn hơn 0 |
| `VNPAY_ALLOWED_AMOUNTS_VND` | Có | Danh sách VND, mỗi giá trị dương và chia hết cho 1.000 |
| `VNPAY_SERVER_IP` | Khi bật QueryDr | IPv4/IPv6 của máy chủ gửi QueryDr |

Ví dụ sandbox:

```dotenv
VNPAY_ENABLED=true
VNPAY_TMN_CODE=DEMOV210
# Ghi khóa Sandbox vào file secrets/vnpay_hash_secret, không đặt trong .env.
VNPAY_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=https://sandbox.example.vn/pay/vnpay/return
VNPAY_QUERY_URL=https://sandbox.vnpayment.vn/merchant_webapi/api/transaction
VNPAY_XU_PER_1000_VND=100
VNPAY_ALLOWED_AMOUNTS_VND=10000,30000,50000,100000,200000,500000
VNPAY_SERVER_IP=203.0.113.10
```

Không dùng credential ví dụ trong production. `VNPAY_ENABLED=true` nhưng cấu hình không hợp lệ sẽ làm giao diện báo VNPAY chưa được cấu hình và endpoint tạo đơn trả HTTP 503.

## 3. Khai báo callback tại VNPAY

- IPN: `https://<ten-mien>/pay/vnpay/ipn`
- Return URL: giá trị chính xác của `VNPAY_RETURN_URL`

Hai URL phải truy cập công khai qua HTTPS. Không đặt xác thực HTTP, CAPTCHA hoặc redirect đăng nhập trước IPN. Reverse proxy phải giữ nguyên toàn bộ query parameter.

Return URL không phải bằng chứng quyết toán vì phụ thuộc trình duyệt người dùng. Chỉ IPN hợp lệ hoặc QueryDr hợp lệ được phép thay đổi trạng thái đơn và cộng Xu.

## 4. QueryDr và đối soát

| Biến | Mặc định | Ràng buộc |
|---|---:|---|
| `VNPAY_RECONCILIATION_ENABLED` | `true` | Tắt bằng `false` nếu chưa được cấp QueryDr |
| `VNPAY_RECONCILIATION_DELAY_MS` | `300000` | Tối thiểu 60.000 ms; cũng là thời gian chờ trước khi replica có thể claim lại đơn |
| `VNPAY_RECONCILIATION_INITIAL_DELAY_MS` | `60000` | Không âm |
| `VNPAY_RECONCILIATION_MIN_AGE_MINUTES` | `20` | Tối thiểu 15 phút |
| `VNPAY_RECONCILIATION_MAX_AGE_DAYS` | `30` | Lớn hơn 0 |
| `VNPAY_RECONCILIATION_BATCH_SIZE` | `50` | Từ 1 đến 500 |

Scheduler chỉ chọn đơn VNPAY đang chờ, có `account_amount > 0`, nằm trong cửa sổ tuổi cấu hình và chưa được replica khác claim gần đây. QueryDr có timeout kết nối 5 giây, timeout request 10 giây, kiểm tra checksum phản hồi, merchant, command, mã đơn, số tiền và loại giao dịch.

Kết quả QueryDr:

- thành công: đơn chuyển sang thành công và cộng đúng số Xu đã chốt;
- thất bại: đơn chuyển sang thất bại, không cộng Xu;
- đang xử lý/không xác định/API lỗi: giữ đơn ở trạng thái chờ để lần sau đối soát lại.

### QueryDr cho VNPAY Recurring

VNPAY Recurring dùng TmnCode và HashSecret riêng do VNPAY cấp, nhưng truy vấn trạng thái qua cùng API
QueryDr. Khi request `recurring_pay` trả kết quả không xác định, hệ thống chuyển attempt sang `UNKNOWN`
và cycle sang `PROVIDER_PENDING`; tuyệt đối chưa chạy nguồn fallback ở thời điểm này.

| Biến | Mặc định | Ràng buộc |
|---|---|---|
| `VNPAY_RECURRING_QUERY_URL` | Sandbox QueryDr | URL HTTP(S) do VNPAY cấp |
| `VNPAY_RECURRING_SERVER_IP` | `127.0.0.1` | IPv4/IPv6 của máy gọi QueryDr; production phải dùng IP công khai đã đăng ký |
| `VNPAY_RECURRING_QUERY_DELAY_MS` | `300000` | Từ 60.000 đến 3.600.000 ms; đồng thời là lease chống hai replica query cùng cycle |
| `VNPAY_RECURRING_REVOCATION_DELAY_MS` | `60000` | Chu kỳ quét mandate cần thu hồi, từ 10.000 đến 3.600.000 ms |
| `VNPAY_RECURRING_REVOCATION_RETRY_DELAY_MS` | `300000` | Thời gian chờ thử lại khi provider chưa xác nhận, từ 60.000 đến 86.400.000 ms |
| `VNPAY_RECURRING_REVOCATION_LEASE_MS` | `60000` | Lease chống hai replica cùng gửi lệnh hủy; phải bao phủ cả request xác thực và request hủy |
| `VNPAY_RECURRING_REVOCATION_BATCH_SIZE` | `50` | Số mandate tối đa mỗi lượt, từ 1 đến 500 |

Worker QueryDr ký bằng bộ secret Recurring và kiểm tra checksum, command, TmnCode, order reference,
số tiền snapshot, loại giao dịch và trạng thái phản hồi. Hai replica có thể cùng nhìn thấy một cycle,
nhưng optimistic lease chỉ cho một replica gửi QueryDr. Kết quả được xử lý như sau:

- `00`: settlement cycle và cấp kỳ thuê bao đúng một lần;
- `01`, `04`, `05`, `06`, `07`, `09` hoặc lỗi kết nối/chữ ký: tiếp tục `PROVIDER_PENDING`, không
  thử fallback; các trạng thái đảo tiền, nghi ngờ gian lận và hoàn trả phải được đối soát an toàn;
- chỉ `02` là thất bại cuối tự động; chuyển sang lịch retry 24/72 giờ, sau đó mới xét nguồn fallback.

Khi người dùng hủy thuê bao cuối kỳ, transaction nội bộ dừng auto-renew ngay và chuyển mandate sang
`REVOKE_PENDING`. Worker gửi `cancel_recurring` ngoài transaction, dùng lease để chỉ một replica gọi
provider. Mã `00`, `04` (mandate không còn) và `12` (token không còn) đều đạt trạng thái mong muốn;
các mã/lỗi mạng khác giữ hàng đợi và thử lại. Chỉ sau khi provider xác nhận, hệ thống chuyển
`REVOKED`, xóa ciphertext token và ngày hết hạn. Việc hủy trong giao diện vì vậy không phụ thuộc độ
sẵn sàng tức thời của VNPAY nhưng vẫn fail-closed, audit được và không làm mất quyền đọc kỳ hiện tại.

Admin có hàng đợi riêng trên trang quản trị thuê bao để xem `PENDING`, `FAILED`, `REVOKE_PENDING` và
`REVOKED` mà không trả ciphertext token ra trình duyệt. Thao tác “thử thu hồi lại” chỉ áp dụng cho
`REVOKE_PENDING`, bắt buộc lý do 8–500 ký tự, optimistic version và chỉ được chạy khi lease worker đã
hết. Thao tác chỉ đặt lại lịch retry; không sửa token, không giả lập phản hồi provider và được ghi vào
`reading_subscription_mandate_admin_audit` bất biến.

Gauge `novel_subscription_renewal_queue{state="mandate_revoke_pending"}` phản ánh tổng mandate đang
chờ thu hồi kể cả khi Recurring tạm tắt. Prometheus cảnh báo mức warning khi queue khác 0 liên tục
15 phút; vận hành phải kiểm tra mã lỗi gần nhất và chỉ dùng retry có audit, không sửa DB trực tiếp.

`transaction.mcDate` và `order.orderReference` dùng cho QueryDr được lấy từ attempt đã lưu, không tạo
lại theo thời điểm truy vấn. Đặc tả QueryDr chính thức có bảng ánh xạ riêng cho “Thanh toán định kỳ”:
<https://sandbox.vnpayment.vn/apis/docs/truy-van-hoan-tien/querydr%26refund.html>.

## 5. Trạng thái và dữ liệu đơn

| Trường | Giá trị |
|---|---|
| `pay_channel` | `4` cho VNPAY |
| `pay_status` | `0`: thất bại, `1`: thành công, `2`: chờ thanh toán |
| `total_amount` | Số tiền VND của đơn |
| `account_amount` | Số Xu đã chốt khi tạo đơn |
| `out_trade_no` | Mã đơn duy nhất phía Novel Plus |
| `trade_no` | Mã giao dịch phía VNPAY sau xác nhận |

Migration [sql/20260716_vnpay_hardening.sql](sql/20260716_vnpay_hardening.sql) thêm `account_amount`, unique index `uk_order_pay_out_trade_no` và index `idx_order_pay_vnpay_reconcile`. Migration có thể chạy lại và sẽ dừng nếu dữ liệu cũ có mã đơn trùng.

## 6. Kiểm tra sandbox

### Chuẩn bị môi trường UAT để đăng ký IPN

Trước tiên trỏ DNS của bốn hostname UAT về máy chạy Compose và mở TCP 80/443. Chuẩn bị cấu hình,
secret cùng nội dung email đăng ký mà không ghi credential vào Git:

```powershell
$env:VNPAY_SANDBOX_HASH_SECRET_FILE = 'C:\secure\vnpay-sandbox-hash-secret'
$env:VNPAY_SANDBOX_CARD_NUMBER = '<so-the-test-do-vnpay-cap>'
$env:VNPAY_SANDBOX_CARD_HOLDER = '<chu-the-test>'
$env:VNPAY_SANDBOX_CARD_DATE = '<MM/YY>'
$env:VNPAY_SANDBOX_OTP = '<otp-test>'
./scripts/prepare-vnpay-uat.ps1 `
  -Domain 'uat.example.vn' `
  -TmnCode '<ma-sandbox-8-ky-tu>' `
  -AcmeEmail 'ops@example.vn'
```

Lệnh sinh ba artifact ignored: `.env.uat`, `secrets/uat/` và `.vnpay-uat-registration.txt`. Merchant
secret và dữ liệu thẻ test chỉ nằm trong `secrets/uat/`, không xuất hiện trong `.env.uat` hoặc nội dung
đăng ký gửi VNPAY. Sau đó dựng
stack, migrate, nạp test data UAT, kiểm tra TLS/channel/IPN fail-closed và tạo thử một URL checkout đúng
merchant, mệnh giá cùng Return URL mà chưa thực hiện giao dịch:

```powershell
./scripts/start-vnpay-uat.ps1
```

`start-vnpay-uat.ps1` tự chạy cả preflight callback và preflight checkout. Chỉ gửi nội dung
`.vnpay-uat-registration.txt` sau khi cả hai đạt. Không gửi file secret. Sau khi
VNPAY xác nhận đã cấu hình IPN, chạy giao dịch kiểm chứng server-to-server:

```powershell
./scripts/test-vnpay-uat.ps1
```

Tại thời điểm này, không cần sửa thêm code hoặc image. Các phần migrate database, test account, dữ
liệu nạp Xu, trình duyệt Playwright và đối soát ledger đã nằm trong harness. Hai việc ngoại vi còn lại
là: DNS/TLS của domain UAT hoạt động và VNPAY xác nhận đúng IPN/Return URL. Script test tự đọc dữ liệu
thẻ Sandbox từ file secret đã tạo; biến môi trường cùng tên chỉ dùng khi cần ghi đè tạm thời.
Credential one-off không phải credential Recurring; giữ
`VNPAY_RECURRING_ENABLED=false` cho tới khi VNPAY cấp riêng dịch vụ Recurring.

Script test tự chạy `npm ci` trong image `node:22-alpine` và chạy Chromium trong image Playwright
được pin cùng phiên bản package. Máy UAT chỉ cần PowerShell và Docker; không phải cài Node hoặc
Chromium trên host. Từ lần chạy sau có thể thêm `-SkipNpmInstall` để dùng lại volume dependency.

`test-vnpay-uat.ps1` không chuyển tiếp IPN đầu tiên. Nó chỉ đạt khi VNPAY tự gọi URL đã đăng ký, đơn
chuyển `SUCCESS`, lần replay của harness trả `02`, số Xu tăng đúng, projection ví khớp và giao dịch
sổ cái kép cân bằng. Dừng UAT nhưng giữ dữ liệu bằng:

```powershell
docker compose --env-file .env.uat -p novel-plus-uat `
  -f compose.yaml -f compose.e2e.yaml down
```

Smoke tự động trên database cô lập đọc checksum từ file ngoài Git và thông tin thẻ/OTP test từ biến
môi trường tiến trình:

```powershell
$env:VNPAY_SANDBOX_HASH_SECRET_FILE = 'C:\secure\vnpay-sandbox-hash-secret'
$env:VNPAY_SANDBOX_TMN_CODE = '<ma-website-sandbox-8-ky-tu>'
$env:VNPAY_SANDBOX_CARD_NUMBER = '<so-the-test-do-vnpay-cap>'
$env:VNPAY_SANDBOX_CARD_HOLDER = '<chu-the-test>'
$env:VNPAY_SANDBOX_CARD_DATE = '<MM/YY>'
$env:VNPAY_SANDBOX_OTP = '<otp-test>'
./scripts/run-vnpay-sandbox-smoke.ps1
```

Runner migrate/seed MySQL mới, chỉ publish `front` trên loopback, thực hiện thanh toán NCB Sandbox,
kiểm tra Return, IPN, replay, số Xu và ledger zero-sum rồi tự xóa container, volume và secret tạm.
Report generated nằm tại `e2e/test-results/vnpay-sandbox-smoke.json` và không chứa checksum, thẻ, OTP
hoặc URL có chữ ký.

Có thể kiểm tra riêng việc đăng nhập, tạo đơn và cấu trúc URL checkout trước khi dùng credential/thẻ test thật:

```powershell
$env:VNPAY_SANDBOX_HASH_SECRET = '<chuoi-ngau-nhien-toi-thieu-32-ky-tu>'
./scripts/run-vnpay-sandbox-smoke.ps1 -TmnCode 'DEMOV210' -CheckoutPreflightOnly
```

Chế độ này chỉ chứng minh luồng cấu hình nội bộ: đơn phải còn `pending`, số dư không đổi và chưa có ledger.
Nó không thay thế checkout bằng credential Sandbox thật hoặc provider-delivered IPN qua URL đã đăng ký.

Harness chủ động chuyển tiếp payload Return do VNPAY ký sang IPN local để kiểm tra settlement. Kết quả
này **không** chứng minh VNPAY server gọi được callback. Gate server-to-server chỉ đạt khi VNPAY gọi
trực tiếp `https://<ten-mien>/pay/vnpay/ipn` đã đăng ký và log/audit ghi nhận request đó.

1. Chạy migration và khởi động stack với credential sandbox.
2. Xác nhận `/pay/index.html` hiển thị VNPAY và đúng mệnh giá VND/Xu.
3. Thanh toán một giao dịch sandbox.
4. Xác nhận Return URL chỉ hiển thị kết quả, chưa tự cộng Xu nếu IPN chưa đến.
5. Kiểm tra đơn và số dư sau IPN:

   ```sql
   SELECT out_trade_no, trade_no, pay_channel, total_amount,
          account_amount, user_id, pay_status, create_time, update_time
   FROM order_pay
   ORDER BY id DESC
   LIMIT 20;
   ```

6. Gửi lại cùng callback theo công cụ sandbox và xác nhận số dư không tăng lần hai.
7. Kiểm tra QueryDr bằng một đơn chờ quá `VNPAY_RECONCILIATION_MIN_AGE_MINUTES`.

Không xác nhận production chỉ bằng credential sandbox. URL, mã website, secret và IP server phải được thay đồng bộ bằng bộ production do VNPAY cấp.

## 7. Xử lý sự cố

### Trang báo VNPAY chưa được cấu hình

Kiểm tra `VNPAY_ENABLED`, mã website đúng 8 ký tự, secret không rỗng, URL hợp lệ, timezone và toàn bộ ràng buộc QueryDr. Xem biến thực tế trong container mà không in secret ra log:

```bash
docker compose exec front sh -c 'printf "enabled=%s tmn=%s query=%s\n" "$VNPAY_ENABLED" "$VNPAY_TMN_CODE" "$VNPAY_QUERY_URL"'
```

### IPN trả checksum không hợp lệ

Đối chiếu đúng secret của môi trường, bảo đảm reverse proxy không sửa/xóa query parameter và không trộn credential sandbox với URL production. Không log `VNPAY_HASH_SECRET`.

### Return báo đang xử lý

Đây là trạng thái bình thường khi trình duyệt quay về trước IPN. Kiểm tra log `front`, khả năng VNPAY truy cập IPN và chờ QueryDr đối soát. Không sửa tay số dư chỉ dựa trên ảnh chụp Return URL.

### Đơn chờ không được QueryDr xử lý

Kiểm tra QueryDr đã bật, tuổi đơn nằm trong cửa sổ cấu hình, `account_amount` lớn hơn 0, URL/IP server đúng và log có lỗi HTTP/checksum. Các đơn cũ hơn `VNPAY_RECONCILIATION_MAX_AGE_DAYS` không được tự động truy vấn.

### Cần khóa thanh toán khẩn cấp

Đặt `VNPAY_ENABLED=false` và recreate `front`:

```bash
docker compose up -d --force-recreate front
```

Thao tác này ngăn tạo đơn mới nhưng không xóa lịch sử. Trước khi tắt QueryDr riêng, cần đối soát các đơn đang chờ.

## 8. Yêu cầu vận hành

- Không lưu merchant secret trong Git, image, ticket hoặc log.
- Theo dõi tỷ lệ IPN lỗi, đơn chờ quá tuổi và lỗi QueryDr.
- Đối soát tổng tiền VNPAY với `order_pay` định kỳ.
- Sao lưu database trước khi đổi tỷ lệ Xu hoặc migration thanh toán.
- Khi đổi `VNPAY_XU_PER_1000_VND`, đơn đã tạo vẫn dùng `account_amount` cũ; chỉ đơn mới dùng tỷ lệ mới.
