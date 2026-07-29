# Runbook triển khai Novel Plus

Tài liệu này áp dụng cho bộ Docker Compose tại thư mục gốc. Stack gồm MySQL, Redis, service migration one-shot, cổng đọc `front`, trình thu thập `crawl` và trang quản trị `admin`.

## 1. Chuẩn bị

- Docker Engine có Docker Compose v2.
- Máy chủ Linux x64/arm64 đủ tài nguyên để build bốn module Maven bằng JDK 21.
- Tên miền và reverse proxy TLS cho môi trường production.
- Bản sao lưu database nếu đây là lần nâng cấp.

Tạo tệp môi trường và thay mọi giá trị `change-me`:

```bash
cp .env.example .env
```

Không commit `.env`. Tối thiểu phải đổi mật khẩu MySQL/Redis, `JWT_SECRET`, `CACHE_MANAGER_PASSWORD` và mật khẩu crawler. `JWT_SECRET` nên là chuỗi ngẫu nhiên dài ít nhất 32 ký tự.

Kiểm tra Compose trước khi build:

```bash
docker compose config --quiet
```

## 2. Cài mới

```bash
docker compose up -d --build
docker compose ps --all
docker compose logs --tail=200 migrate front crawl admin
```

Service `migrate` phải kết thúc với mã `0`. Ba service ứng dụng phải chuyển sang `healthy` sau giai đoạn khởi động.

Migration `20260726_vietnamese_search.sql` có thể rebuild bảng `book` để tạo cột stored và FULLTEXT ngram. Trên production, phải đo trước trên bản sao dữ liệu và bố trí cửa sổ bảo trì; không khởi động `front` với mã tìm kiếm mới trước khi migration này hoàn tất.

Service migration là container one-shot duy nhất dùng tài khoản MySQL root để tạo schema, index và trigger bất biến của sổ cái. `front`, `crawl` và `admin` luôn kết nối bằng `MYSQL_USER`/`MYSQL_APP_PASSWORD`; không dùng root cho runtime ứng dụng.

Các cổng host mặc định:

| Service | Địa chỉ |
|---|---|
| front | `http://localhost:8083` |
| crawl | `http://localhost:8081` |
| admin | `http://localhost:8080` |

Có thể đổi cổng bằng `FRONT_PORT`, `CRAWL_PORT` và `ADMIN_PORT`. Trong production chỉ nên công khai reverse proxy HTTPS; không công khai trực tiếp MySQL hoặc Redis.

## 3. Dữ liệu bền vững

| Volume | Nội dung |
|---|---|
| `mysql-data` | Database MySQL |
| `redis-data` | Redis AOF |
| `novel-media` | Ảnh bìa và tệp tải lên |
| `novel-books` | Nội dung truyện lưu trên filesystem nếu được bật |

`docker compose down` giữ các volume. `docker compose down -v` xóa toàn bộ dữ liệu của stack và chỉ được dùng khi chủ động hủy môi trường.

Sao lưu MySQL trước mỗi lần nâng cấp. Ví dụ:

```bash
docker compose exec -T mysql sh -c \
  'exec mysqldump --single-transaction --routines --triggers -uroot -p"$MYSQL_ROOT_PASSWORD" "$MYSQL_DATABASE"' \
  > novel-plus-backup.sql
```

Kiểm tra file dump không rỗng và định kỳ diễn tập phục hồi trên database riêng. Sao lưu thêm volume `novel-media` và `novel-books` nếu đang lưu file cục bộ.

## 4. Nâng cấp

1. Sao lưu database và các volume file.
2. Đọc [hướng dẫn SQL](sql/readme.md), chạy mọi migration trung gian còn thiếu trên bản sao dữ liệu trước.
3. Lấy source/image phiên bản mới.
4. Build lại và khởi động:

   ```bash
   docker compose up -d --build
   ```

5. Xác minh `migrate` kết thúc mã `0`, các ứng dụng healthy và không có lỗi mới:

   ```bash
   docker compose ps --all
   docker compose logs --since=10m migrate front crawl admin
   ```

Các migration từ `20260712_vi_localization.sql` đến `20260728_chapter_commercial_policy.sql` được Compose chạy theo thứ tự và phải chạy lặp lại an toàn. Migration VNPAY chủ động dừng nếu phát hiện `out_trade_no` trùng để tránh tự sửa lịch sử thanh toán. Migration sổ cái chỉ backfill số dư đầu kỳ một lần thông qua `platform_migration_history`; các migration KYC, refund, kiểm duyệt, báo cáo và editor tạo schema/audit cần thiết nhưng không tự sinh dữ liệu định danh. Migration SimHash đổi giá trị số cũ sang chuỗi bit 64 ký tự để đồng bộ với model Java và bảo toàn dữ liệu phát hiện trùng. Migration bìa giữ bìa legacy ở trạng thái đã duyệt và tạo queue riêng cho mọi bìa mới hoặc được thay lại. Migration analytics tạo event đọc ẩn danh append-only; cần chốt chính sách retention trước production. Migration notification chỉ thu sự kiện chương được duyệt sau thời điểm nâng cấp, không gửi lại chương cũ. Migration recommendation chỉ thêm index phục vụ pool đã duyệt và lịch sử mua, không sửa hành vi độc giả. Migration chính sách thương mại chỉ backfill giá preview bản nháp, không tự đổi `is_vip`, giá hoặc thời gian truy cập của chương đã xuất bản.

## 5. Reverse proxy và TLS

- Chuyển tiếp `/` của website tới `front:8083`.
- Chỉ cấu hình callback VNPAY qua HTTPS công khai.
- IPN: `https://<ten-mien>/pay/vnpay/ipn`.
- Return URL: `https://<ten-mien>/pay/vnpay/return`.
- Giữ nguyên query string VNPAY khi proxy.
- Chuyển tiếp IP người dùng bằng `X-Forwarded-For`/`X-Real-IP` từ proxy tin cậy.

Trang admin và crawler nên dùng tên miền riêng, VPN hoặc allowlist IP. Không dùng tài khoản seed `admin/admin` sau lần đăng nhập đầu tiên.

## 6. Kiểm tra sau triển khai

```bash
curl --fail http://localhost:${FRONT_PORT:-8083}/
curl --fail http://localhost:${CRAWL_PORT:-8081}/login.html
curl --fail http://localhost:${ADMIN_PORT:-8080}/login
```

Kiểm tra thêm:

- đăng nhập front/admin/crawler;
- tìm kiếm và đọc một chương;
- quyền ghi/đọc ảnh tải lên;
- kết nối Redis/MySQL;
- trang nạp Xu hiển thị đúng trạng thái VNPAY;
- refund chỉ chuyển `REQUESTED → APPROVED` sau khi giữ đủ Xu, chỉ chuyển `APPROVED → REVERSED` khi có mã xác nhận provider; nhánh provider fail phải trả Xu và đưa `REFUND_CLEARING` về 0;
- chargeback toàn phần khi độc giả đã tiêu Xu phải đưa ví sang `DEBT`; nạp mới bù nợ trước và mọi giao dịch vẫn có tổng entry bằng 0;
- trang `/novel/authorFinance` chỉ hiển thị dữ liệu rõ cho tài khoản có quyền `novel:authorFinance:pii`;
- thử một yêu cầu rút bị từ chối và một yêu cầu thanh toán thành công; cả hai phải đưa `PAYOUT_CLEARING` về 0;
- retry yêu cầu rút với cùng khóa idempotency không được tạo hold hoặc mã yêu cầu thứ hai;
- theo dõi/bỏ theo dõi một tác giả, xuất bản một chương thử và xác minh độc giả chỉ nhận một thông báo dù đồng thời theo dõi cả truyện lẫn tác giả;
- badge chưa đọc, đánh dấu một thông báo và đánh dấu tất cả đã đọc hoạt động trên desktop/mobile;
- log không chứa lỗi migration, checksum hoặc kết nối.

### Giám sát thông báo chương mới

Worker fan-out chỉ chạy trong `front`. Khi `front` dừng, trigger database vẫn giữ event ở trạng thái `PENDING`; không mất sự kiện. Worker retry theo backoff 1, 2, 4, 8, 16, 32 rồi tối đa 60 phút và chuyển sang `FAILED` sau lần lỗi thứ 10. Theo dõi backlog bằng:

```sql
SELECT status, COUNT(*) AS event_count, MIN(create_time) AS oldest_event
FROM chapter_publish_event
GROUP BY status;

SELECT id, chapter_id, attempts, next_attempt_time, last_error
FROM chapter_publish_event
WHERE status = 'FAILED'
ORDER BY id;
```

Không tự xóa hoặc đổi trạng thái event lỗi trước khi xử lý nguyên nhân trong `last_error`. Sau khi đã khắc phục, quản trị viên có thể đưa từng event về hàng đợi bằng thao tác có kiểm soát theo [hướng dẫn thông báo chương mới](chapter-notifications.md). Hai biến `NOTIFICATION_CHAPTER_SCHEDULE_DELAY_MS` và `NOTIFICATION_CHAPTER_SCHEDULE_INITIAL_DELAY_MS` điều chỉnh nhịp worker; chỉ giảm sau khi đo tải MySQL.

Nếu nhận KYC, phải cấu hình cùng một `PII_ENCRYPTION_KEY` AES-256 Base64 từ secret manager cho `front` và `admin`. Không bật `AUTHOR_PAYOUT_ENABLED` trước khi hoàn thành quy trình bốn mắt, chuyển khoản, đối soát ngân hàng và chính sách thuế mô tả trong [tài chính tác giả](author-finance.md).

VietQR mặc định tắt và chỉ được bật khi có tài khoản nhận tiền thật cùng webhook secret ngẫu nhiên tối thiểu 32 ký tự. Tích hợp hiện là QR chuyển khoản + webhook xác thực; không tự suy đoán giao dịch thành công. Adapter NAPAS chưa có hợp đồng/API ngân hàng thật sẽ trả `PROVIDER_NOT_CONFIGURED`, không sinh mã giao dịch giả. `MANUAL_BANK` cho payout cũng chỉ ghi nhận thao tác vận hành và mã tham chiếu.

Phát hành chứng từ tài chính mặc định tắt. Chỉ đặt `FINANCIAL_VOUCHER_ISSUANCE_ENABLED=true` sau khi đã cấu hình `PLATFORM_LEGAL_NAME`, `PLATFORM_TAX_CODE` và được phê duyệt cách tính/ghi nhận thuế. Các file PDF/CSV/JSON trong module này là chứng từ vận hành kỹ thuật, không mặc nhiên là hóa đơn điện tử hợp pháp.

### Kiểm tra đóng gói theme

Khi kiểm tra artifact Maven ngoài Docker, luôn chạy lifecycle từ `generate-resources` trở lên để bước dọn output theme được thực thi:

```powershell
foreach ($theme in @('green', 'orange', 'dark', 'blue')) {
    mvn -pl novel-front -am -DskipTests -Dtheme.name=$theme package
}
```

Không dùng trực tiếp `mvn resources:resources` để chuyển theme. Docker build tạo bốn thư mục overlay độc lập trong `/workspace/packaged-themes`, còn Maven xóa riêng hai thư mục resource đã đóng gói trước mỗi lifecycle; cả hai đường đều giữ thứ tự `runtime base → theme overlay`.

## 7. Rollback

Rollback image/source về phiên bản trước và chạy lại `docker compose up -d --build`. Không tự động rollback schema bằng cách chạy migration ngược. Nếu phiên bản cũ không tương thích schema mới, phục hồi database từ bản sao lưu đã kiểm chứng và phục hồi đồng bộ các volume file.

Khi lỗi chỉ nằm ở VNPAY, đặt `VNPAY_ENABLED=false` rồi recreate `front` để khóa tạo giao dịch mới trong khi vẫn giữ nguyên lịch sử đơn:

```bash
docker compose up -d --force-recreate front
```

## 8. Kiểm tra trước khi phát hành

Profile Maven `central-repo` là một phần của quy trình đóng gói Docker: profile này ghi đè cả repository dependency và plugin sang Maven Central. Không xóa profile hoặc đổi ID repository mà không kiểm tra lại effective POM, vì Dockerfile bật profile này để tránh phụ thuộc độ sẵn sàng của mirror Aliyun.

```bash
mvn -B -ntp -Pcentral-repo test
node scripts/verify-i18n.mjs
docker compose --env-file .env.example config --quiet
git diff --check
```

Xem [hướng dẫn VNPAY](vnpay.md) trước khi bật thanh toán sandbox hoặc production.
