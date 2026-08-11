# Secret file cho Docker Compose

Tạo tám tệp không có phần mở rộng trong thư mục này trước khi deploy:

- `mysql_root_password`
- `mysql_app_password`
- `redis_password`
- `jwt_secret`
- `cache_manager_password`
- `pii_encryption_key`
- `gamification_vote_ip_hash_salt`: salt riêng tối thiểu 32 ký tự, khớp với `GAMIFICATION_VOTE_IP_HASH_KEY_ID`; không dùng chung JWT, Redis hoặc database.
- `admin_bootstrap_password`
- `crawler_admin_password`
- `backup_encryption_password`
- `vnpay_hash_secret`
- `vnpay_recurring_password`
- `vnpay_recurring_client_secret`
- `vnpay_recurring_hash_secret`
- `vietqr_webhook_secret`
- `alertmanager_webhook_url`: URL HTTPS của webhook vận hành nhận cảnh báo.
- `grafana_admin_password`: mật khẩu quản trị Grafana, tối thiểu 16 ký tự ngẫu nhiên.

Mỗi tệp chỉ chứa một giá trị secret và một newline cuối tệp là tùy chọn. Với provider đang tắt, năm file provider có thể chứa `disabled`; validator sẽ từ chối khởi động nếu bật provider mà chưa thay bằng secret đủ mạnh. Webhook Alertmanager phải dùng HTTPS ở production; chỉ smoke test cục bộ mới đặt `ALERTMANAGER_ALLOW_HTTP=true`. Không commit các tệp này. Có thể đặt chúng ở thư mục ngoài repo và cấu hình `SECRETS_DIR` bằng đường dẫn tuyệt đối.
