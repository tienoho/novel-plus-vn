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

Migration `20260712_vi_localization.sql` và `20260716_vnpay_hardening.sql` có thể chạy lặp lại. Migration VNPAY chủ động dừng nếu phát hiện `out_trade_no` trùng để tránh tự sửa lịch sử thanh toán.

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
- log không chứa lỗi migration, checksum hoặc kết nối.

## 7. Rollback

Rollback image/source về phiên bản trước và chạy lại `docker compose up -d --build`. Không tự động rollback schema bằng cách chạy migration ngược. Nếu phiên bản cũ không tương thích schema mới, phục hồi database từ bản sao lưu đã kiểm chứng và phục hồi đồng bộ các volume file.

Khi lỗi chỉ nằm ở VNPAY, đặt `VNPAY_ENABLED=false` rồi recreate `front` để khóa tạo giao dịch mới trong khi vẫn giữ nguyên lịch sử đơn:

```bash
docker compose up -d --force-recreate front
```

## 8. Kiểm tra trước khi phát hành

```bash
mvn -B -ntp -Pcentral-repo test
node scripts/verify-i18n.mjs
docker compose --env-file .env.example config --quiet
git diff --check
```

Xem [hướng dẫn VNPAY](vnpay.md) trước khi bật thanh toán sandbox hoặc production.
