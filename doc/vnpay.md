# Tích hợp và vận hành VNPAY

Novel Plus dùng giao thức VNPAY 2.1.0 với HMAC-SHA512. VNPAY là kênh tạo giao dịch mới duy nhất trong giao diện; mã kênh lưu tại `order_pay.pay_channel` là `4`. Dữ liệu đơn của Alipay/WeChat cũ chỉ được giữ để đối soát lịch sử và không còn endpoint tạo giao dịch mới.

## 1. Luồng xử lý

1. Người dùng đăng nhập, chọn mệnh giá trên `/pay/index.html` và gửi `POST /pay/vnpay`.
2. Backend chỉ chấp nhận mệnh giá thuộc allowlist, chốt số Xu vào `order_pay.account_amount`, tạo mã đơn duy nhất rồi chuyển hướng sang VNPAY.
3. VNPAY đưa trình duyệt về `GET /pay/vnpay/return`. Return URL chỉ kiểm tra chữ ký và trạng thái đơn để hiển thị kết quả; không cộng Xu.
4. VNPAY gọi `GET /pay/vnpay/ipn`. IPN kiểm tra checksum, mã đơn, kênh và số tiền trước khi cập nhật đơn và số dư trong cùng transaction.
5. Scheduler QueryDr đối soát đơn chờ khi IPN bị mất hoặc gián đoạn.

Callback lặp không cộng Xu lần hai. Số Xu dùng khi hoàn tất lấy từ đơn đã lưu, không tính lại theo tỷ lệ hiện tại.

## 2. Cấu hình merchant

Sao chép `.env.example` thành `.env`, sau đó cấu hình:

| Biến | Bắt buộc khi bật | Ý nghĩa |
|---|---:|---|
| `VNPAY_ENABLED` | Có | Đặt `true` để mở VNPAY; mặc định `false` |
| `VNPAY_TMN_CODE` | Có | Mã website 8 ký tự chữ/số do VNPAY cấp |
| `VNPAY_HASH_SECRET` | Có | Khóa HMAC do VNPAY cấp |
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
VNPAY_HASH_SECRET=thay-bang-khoa-sandbox-do-vnpay-cap
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
