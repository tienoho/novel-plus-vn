# Runbook triển khai Novel Plus

Tài liệu này áp dụng cho bộ Docker Compose tại thư mục gốc. Stack gồm MySQL, Redis, service migration one-shot, `front`, `crawl`, `admin`, Prometheus, Alertmanager, Pushgateway, Grafana và Caddy làm cổng vào HTTPS duy nhất. Các công cụ backup/restore nằm trong profile `tools` và không tự khởi động cùng ứng dụng.

## 1. Chuẩn bị

- Docker Engine có Docker Compose v2.
- Máy chủ Linux x64/arm64 có Docker Compose v2, GitHub CLI và Node.js 22. Build từ source cần thêm JDK 21/Maven.
- Bốn tên miền trỏ về máy chủ: website, admin, crawler và Grafana. Cổng 80/443 phải truy cập được từ Internet để Caddy cấp và gia hạn TLS.
- Bản sao lưu database nếu đây là lần nâng cấp.

Tạo tệp môi trường, thư mục secret và backup bên ngoài repo:

```bash
cp .env.example .env
sudo install -d -m 0700 /etc/khoi-thu/secrets
sudo install -d -o 10002 -g 10002 -m 0700 /var/backups/khoi-thu
```

Đặt `SECRETS_DIR=/etc/khoi-thu/secrets` và `BACKUP_DIR=/var/backups/khoi-thu` trong `.env`. Tạo đủ các file được liệt kê tại [secrets/README.md](../secrets/README.md), đặt quyền `0600`, chủ sở hữu là tài khoản vận hành Docker. `pii_encryption_key` phải là Base64 của đúng 32 byte; các secret còn lại phải là chuỗi ngẫu nhiên dài. Không đặt secret trực tiếp trong `.env` và không commit `.env` hay thư mục secret thật.

Kiểm tra Compose trước khi build:

```bash
docker compose config --quiet
```

## 2. Cài mới

### 2.1. Production từ release đã phê duyệt

Không build lại mười image first-party trên máy production. Checkout đúng tag đã publish, tải
toàn bộ bằng chứng từ GitHub Release, rồi sinh `.env.release` từ manifest và promotion record:

```bash
export TAG=v1.2.3
git fetch --tags origin
git checkout --detach "$TAG"
mkdir -p release-meta release-artifacts
gh release download "$TAG" --dir release-meta \
  --pattern 'release-manifest.json' \
  --pattern 'release-manifest.sha256' \
  --pattern 'production-promotion-record.json'
gh release download "$TAG" --dir release-artifacts \
  --pattern 'digest-*' \
  --pattern 'trivy-novel-*' \
  --pattern 'sbom-release-*' \
  --pattern 'attestation-*'
node scripts/generate-release-manifest.mjs deploy-env \
  --artifacts release-artifacts \
  --manifest release-meta/release-manifest.json \
  --checksum release-meta/release-manifest.sha256 \
  --promotion-record release-meta/production-promotion-record.json \
  --output .env.release \
  --tag "$TAG"
```

`deploy-env` kiểm tra lại checksum/artifact, tag/commit, hash manifest trong promotion record và bằng
chứng biên bản đã ký trước khi ghi đúng mười biến `NOVEL_*_IMAGE=subject@sha256:...`. Tệp này không có
secret nhưng vẫn bị loại khỏi Git để tránh triển khai nhầm RC. Kiểm tra và pull đúng digest:

```bash
docker compose --env-file .env --env-file .env.release config --quiet
docker compose --env-file .env --env-file .env.release --profile tools pull \
  mysql migrate front crawl admin caddy alertmanager pushgateway grafana backup
docker compose --env-file .env --env-file .env.release up -d --no-build
docker compose --env-file .env --env-file .env.release ps --all
```

`mysql`, `front`, `admin`, `crawl`, `migrate`, `caddy`, `alertmanager`, `pushgateway` và `backup` phải resolve thành image có
`@sha256:`. Ba service `backup`, `restore`, `restore-drill` dùng chung một image backup đã attest.

### 2.2. Build trực tiếp từ source cho development/UAT

Lệnh dưới đây không được dùng để triển khai một release production đã phê duyệt vì artifact sinh ra
không còn là digest trong biên bản:

```bash
docker compose up -d --build
docker compose ps --all
docker compose logs --tail=200 migrate front crawl admin prometheus alertmanager grafana caddy
```

Service `migrate` phải kết thúc với mã `0`. Ba service ứng dụng, Alertmanager, Pushgateway và Grafana phải chuyển sang `healthy`; Prometheus phải báo bốn target `novel-front`, `novel-admin`, `novel-crawl`, `pushgateway` là `up`.

Migration `20260726_vietnamese_search.sql` có thể rebuild bảng `book` để tạo cột stored và FULLTEXT ngram. Trên production, phải đo trước trên bản sao dữ liệu và bố trí cửa sổ bảo trì; không khởi động `front` với mã tìm kiếm mới trước khi migration này hoàn tất.

Service migration là container one-shot duy nhất dùng tài khoản MySQL root để tạo schema, index và trigger bất biến của sổ cái. `front`, `crawl` và `admin` luôn kết nối bằng `MYSQL_USER`/`MYSQL_APP_PASSWORD`; không dùng root cho runtime ứng dụng.

Các cổng host production:

| Service | Địa chỉ |
|---|---|
| Caddy HTTP | `:80` (tự chuyển sang HTTPS) |
| Caddy HTTPS/HTTP3 | `:443` TCP/UDP |

`front`, `crawl`, `admin`, MySQL và Redis chỉ `expose` trong network Compose, không publish ra host. Chỉ integration test được phép mở MySQL loopback bằng override rõ ràng:

```bash
docker compose -f compose.yaml -f compose.test.yaml up -d mysql
```

## 3. Dữ liệu bền vững

| Volume | Nội dung |
|---|---|
| `mysql-data` | Database MySQL |
| `redis-data` | Redis AOF |
| `prometheus-data` | Chuỗi thời gian Prometheus, mặc định giữ 30 ngày |
| `alertmanager-data` | Trạng thái silences và notification Alertmanager |
| `pushgateway-data` | Kết quả job one-shot, gồm Flyway migrate/validate |
| `grafana-data` | Database và trạng thái Grafana |
| `novel-media` | Ảnh bìa và tệp tải lên |
| `novel-books` | Nội dung truyện lưu trên filesystem nếu được bật |

`docker compose down` giữ các volume. `docker compose down -v` xóa toàn bộ dữ liệu của stack và chỉ được dùng khi chủ động hủy môi trường.

Sao lưu mã hóa trước mỗi lần nâng cấp:

```bash
docker compose --profile tools run --rm backup
```

Lệnh tạo một gói GPG AES-256 chứa dump nhất quán, trigger/routine/event, `novel-media`, `novel-books` và checksum SHA-256 trong `BACKUP_DIR`. Secret mã hóa nằm tại `backup_encryption_password`, tách khỏi bản backup.

Diễn tập tự động trên database tạm, không ghi đè dữ liệu hiện tại:

```bash
BACKUP_ARCHIVE=khoi-thu-YYYYMMDDTHHMMSSZ.tar.gz.gpg \
docker compose --profile tools run --rm restore-drill
```

Phục hồi thật là thao tác phá hủy, phải dừng ba ứng dụng và Caddy, xác nhận rõ rồi chạy:

```bash
docker compose stop caddy front admin crawl
BACKUP_ARCHIVE=khoi-thu-YYYYMMDDTHHMMSSZ.tar.gz.gpg RESTORE_CONFIRM=RESTORE \
docker compose --profile tools run --rm restore
docker compose up -d
```

Sau phục hồi phải chạy `migrate`, kiểm tra checksum Flyway, health và các luồng smoke. Không xem một file backup là hợp lệ cho tới khi `restore-drill` đạt trên máy/database tách biệt.

## 4. Nâng cấp

1. Sao lưu database và các volume file.
2. Đọc [hướng dẫn SQL](sql/readme.md), chạy mọi migration trung gian còn thiếu trên bản sao dữ liệu trước.
3. Lấy tag, manifest, promotion record và image digest của bản đã phê duyệt theo mục 2.1.
4. Pull cả mười image theo digest và khởi động không build lại source:

   ```bash
   docker compose --env-file .env --env-file .env.release --profile tools pull \
     mysql migrate front crawl admin caddy alertmanager pushgateway grafana backup
   docker compose --env-file .env --env-file .env.release up -d --no-build
   ```

5. Xác minh `migrate` kết thúc mã `0`, các ứng dụng healthy và không có lỗi mới:

   ```bash
   docker compose ps --all
   docker compose logs --since=10m migrate front crawl admin
   ```

Image `khoi-thu/migrations` dùng Flyway 13.1.0 và chỉ giữ các plugin/driver cần cho MySQL; không xóa driver riêng lẻ khi plugin `ServiceLoader` tương ứng vẫn còn. Compose chạy baseline cùng 41 migration tăng dần, từ `20260712_vi_localization.sql` đến `20260819_gamification_dynamic_config_p1_hardening.sql`, sau đó validate checksum trước khi mở các ứng dụng. Không sửa migration đã phát hành, không chạy lại bằng shell loop và không dùng `novel_plus_data.sql.zip` trong image release.

Migration VNPAY chủ động dừng nếu phát hiện `out_trade_no` trùng để tránh tự sửa lịch sử thanh toán. Migration sổ cái chỉ backfill số dư đầu kỳ một lần thông qua `platform_migration_history`; các migration KYC, refund, kiểm duyệt, báo cáo và editor tạo schema/audit cần thiết nhưng không tự sinh dữ liệu định danh. Các migration mới bổ sung BCrypt, thuê bao recurring, kỳ gamification đặc biệt, thưởng level, chống lạm dụng và chính sách công khai. Mọi lỗi checksum hoặc invariant phải chặn deploy để vận hành đối soát, không tự bỏ qua.

### 4.1. Cutover cấu hình Gamification

Production mặc định dùng `GAMIFICATION_CONFIG_SOURCE=DB`. Trước lần cutover đầu tiên, giữ một bản
ENV đã phê duyệt để rollback và thực hiện tuần tự:

1. import ENV thành draft trong `/novel/gamification/settings`;
2. submit, phê duyệt bằng tài khoản khác và schedule revision;
3. chuyển canary sang `DB_SHADOW`, chờ `gamification_config_shadow_diff_keys=0`;
4. chuyển một front sang `DB`, smoke đường đọc/ghi, rồi chuyển admin/crawl và các front còn lại;
5. xác minh revision config/scheduler đồng nhất trước khi xóa 38 biến runtime cũ khỏi deploy.

Chỉ giữ `GAMIFICATION_CONFIG_SOURCE`, refresh/max-stale, `GAMIFICATION_FORCE_DISABLE`, key ID và
salt file. File `secrets/gamification_vote_ip_hash_salt` phải có ít nhất 32 ký tự ngẫu nhiên, không
trùng JWT/Redis/database secret. Khi incident, `FORCE_DISABLE=true` khóa đường ghi ngay; nếu provider
DB không phục hồi, đổi source về `ENV` và restart. Không UPDATE revision cũ hoặc down-migration.

Theo dõi các alert `NovelGamificationConfigRevisionDrift`, `NovelGamificationConfigStale`,
`NovelGamificationConfigRefreshFailed`, `NovelGamificationConfigActivationFailed` và
`NovelGamificationSchedulerRescheduleFailed`; dashboard hiển thị revision config/scheduler và tuổi
snapshot theo từng ứng dụng.

## 5. Caddy, TLS và giới hạn truy cập

- `NOVEL_DOMAIN` chuyển tiếp tới `front:8083`; `NOVEL_ADMIN_DOMAIN` tới `admin:80`; `NOVEL_CRAWL_DOMAIN` tới `crawl:8081`; `NOVEL_GRAFANA_DOMAIN` tới `grafana:3000`.
- Caddy tự cấp/gia hạn TLS, redirect HTTP sang HTTPS, thêm security header, nén, giới hạn body mặc định 25 MB và rate-limit theo IP/network IPv6.
- Rate-limit tại proxy chỉ là lớp bảo vệ thô; rate-limit nghiệp vụ, idempotency và chống lạm dụng trong ứng dụng vẫn bắt buộc.
- Chỉ cấu hình callback VNPAY qua HTTPS công khai.
- IPN: `https://<ten-mien>/pay/vnpay/ipn`.
- Return URL: `https://<ten-mien>/pay/vnpay/return`.
- Giữ nguyên query string VNPAY khi proxy.
- Chuyển tiếp IP người dùng bằng `X-Forwarded-For`/`X-Real-IP` từ proxy tin cậy.
- Caddy trả 404 cho `/actuator` và `/actuator/**` trên ba domain ứng dụng. Không public Prometheus, Alertmanager hoặc Pushgateway.

Trang admin và crawler dùng tên miền riêng; production nên đặt thêm VPN hoặc allowlist IP. Admin đầu tiên chỉ được tạo từ bootstrap secret, dùng BCrypt và bắt buộc đổi mật khẩu sau lần đăng nhập đầu tiên.

`CSP_ENFORCE=true` là mặc định. Mỗi response HTML phải có `Content-Security-Policy` và mọi thẻ `script` phải dùng cùng nonce với header. Front, admin và crawler bắt buộc kiểm tra CSRF cho request ghi; crawler dùng cookie `XSRF-TOKEN` với `Secure; SameSite=Lax` và header `X-XSRF-TOKEN`. Chỉ hạ `CSP_ENFORCE=false` tạm thời để thu thập báo cáo tương thích, không thêm `unsafe-inline` hoặc tắt CSRF.

## 6. Kiểm tra sau triển khai

Trước khi phát hành, chạy smoke Compose cô lập. Script luôn rebuild image từ source hiện tại, tự tạo secret tạm ngoài workspace, chờ Flyway và các service healthy, rồi xác minh CSP nonce, CSRF crawler, bốn target Prometheus, metric `novel_migration_success=1` và dashboard Grafana trước khi xóa container, volume và secret:

```powershell
./scripts/smoke-admin-compose.ps1 -TimeoutSeconds 600
```

Bài diễn tập backup/restore dùng secret triển khai đã cấu hình và phục hồi sang database tạm, không thay thế database chính:

```powershell
./scripts/verify-backup-restore.ps1
```

```bash
curl --fail --proto '=https' "https://${NOVEL_DOMAIN}/"
curl --fail --proto '=https' "https://${NOVEL_CRAWL_DOMAIN}/login.html"
curl --fail --proto '=https' "https://${NOVEL_ADMIN_DOMAIN}/login"
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

Nếu nhận KYC, phải cấu hình cùng một file secret `pii_encryption_key` AES-256 Base64 cho `front` và `admin`. Không bật `AUTHOR_PAYOUT_ENABLED` trước khi hoàn thành quy trình bốn mắt, chuyển khoản, đối soát ngân hàng và chính sách thuế mô tả trong [tài chính tác giả](author-finance.md).

VietQR mặc định tắt và chỉ được bật khi có tài khoản nhận tiền thật cùng webhook secret ngẫu nhiên tối thiểu 32 ký tự. Tích hợp hiện là QR chuyển khoản + webhook xác thực; không tự suy đoán giao dịch thành công. Adapter NAPAS chưa có hợp đồng/API ngân hàng thật sẽ trả `PROVIDER_NOT_CONFIGURED`, không sinh mã giao dịch giả. `MANUAL_BANK` cho payout cũng chỉ ghi nhận thao tác vận hành và mã tham chiếu.

Phát hành chứng từ tài chính mặc định tắt. Chỉ đặt `FINANCIAL_VOUCHER_ISSUANCE_ENABLED=true` sau khi đã cấu hình `PLATFORM_LEGAL_NAME`, `PLATFORM_TAX_CODE` và được phê duyệt cách tính/ghi nhận thuế. Các file PDF/CSV/JSON trong module này là chứng từ vận hành kỹ thuật, không mặc nhiên là hóa đơn điện tử hợp pháp.

### Kiểm tra đóng gói theme

Khi kiểm tra artifact Maven ngoài Docker, luôn chạy lifecycle từ `generate-resources` trở lên để bước dọn output theme được thực thi:

```powershell
foreach ($theme in @('green', 'orange', 'dark', 'blue')) {
    mvn -pl novel-front -am -DskipTests -Dtheme.name=$theme package
}
```

Pha đóng gói sao chép script từ `novel-front/src/main/build/scripts` sang
`novel-front/target/build/bin` rồi mới chuẩn hóa line ending Unix. Sau build, source phải giữ nguyên;
riêng file rỗng `novel-front.sh` có SHA-256
`E3B0C44298FC1C149AFBF4C8996FB92427AE41E4649B934CA495991B7852B855`. Entry script trong ZIP được
đóng gói với mode `100755`.

`novel-front/target/build/novel-front.zip` phải chứa `Dockerfile`, `novel-front.jar`, thư mục `bin`,
`config` và `templates`. Cấu hình AI trong distribution chỉ tham chiếu `OPENAI_API_KEY`; không ghi
khóa API literal vào source hoặc artifact. Nếu một khóa từng được commit, phải thu hồi khóa đó tại
nhà cung cấp vì thay file hiện tại không xóa bí mật khỏi lịch sử Git.

Không dùng trực tiếp `mvn resources:resources` để chuyển theme. Docker build tạo bốn thư mục overlay độc lập trong `/workspace/packaged-themes`, còn Maven xóa riêng hai thư mục resource đã đóng gói trước mỗi lifecycle; cả hai đường đều giữ thứ tự `runtime base → theme overlay`.

Mỗi thư mục `templates/<theme>` trong ZIP là kết quả merge hoàn chỉnh, không phải overlay thô. Sau
đóng gói, tối thiểu phải xác minh cả `green`, `orange`, `dark`, `blue` đều có các file nền sau, kể cả
khi theme nguồn không định nghĩa chúng:

```powershell
$themes = 'green', 'orange', 'dark', 'blue'
foreach ($theme in $themes) {
    Test-Path "novel-front/target/build/templates/$theme/html/common/monthly_ticket.html"
    Test-Path "novel-front/target/build/templates/$theme/static/service-worker.js"
    Test-Path "novel-front/target/build/templates/$theme/static/javascript/reader-tools.js"
}
```

Smoke test distribution phải chạy JAR với working directory là `novel-front/target/build`, không
chạy JAR đã tách khỏi `config` và `templates`. Trên database thử nghiệm, kiểm tra `actuator/health`,
trang chủ, `/service-worker.js`, một chương dài và luồng lưu–mở lại–tiếp tục vị trí đọc. Nếu bypass
ShardingSphere để kết nối MySQL cô lập, phải override đồng thời URL và driver:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3307/novel_plus'
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'com.mysql.cj.jdbc.Driver'
```

Ngày 30/07/2026, artifact `green` đã đạt health và ba URL đại diện trả `200`; front chạy 303 test
không có failure/error, 20 integration test được skip theo cờ mặc định. Browser smoke xác minh
service worker, toolbar đọc, font/giãn dòng bền qua navigation và khôi phục character offset; User-Agent
iPhone render template mobile và nạp reader tools. Kết quả này chưa thay cho vòng JDK 21 và thiết bị
vật lý trước phát hành production.

## 7. Rollback

Rollback bằng cách tải manifest/promotion record của release trước, sinh lại `.env.release`, pull đúng
digest và chạy `docker compose --env-file .env --env-file .env.release up -d --no-build`. Không build
lại source để giả lập image cũ. Không tự động rollback schema bằng cách chạy migration ngược. Nếu phiên
bản cũ không tương thích schema mới, phục hồi database từ bản sao lưu đã kiểm chứng và phục hồi đồng bộ
các volume file.

Khi lỗi chỉ nằm ở VNPAY, đặt `VNPAY_ENABLED=false` rồi recreate `front` để khóa tạo giao dịch mới trong khi vẫn giữ nguyên lịch sử đơn:

```bash
docker compose up -d --force-recreate front
```

## 8. Kiểm tra trước khi phát hành

Profile Maven `central-repo` là một phần của quy trình đóng gói Docker: profile này ghi đè cả repository dependency và plugin sang Maven Central. Không xóa profile hoặc đổi ID repository mà không kiểm tra lại effective POM, vì Dockerfile bật profile này để tránh phụ thuộc độ sẵn sàng của mirror Aliyun.

```bash
mvn -B -ntp -Pcentral-repo verify
node scripts/verify-i18n.mjs
docker compose config --quiet
docker compose -f compose.yaml -f compose.test.yaml config --quiet
docker compose --profile tools config --quiet
git diff --check
```

Chạy E2E bốn theme, bài tải 500 VU và restore drill trên môi trường cô lập:

```powershell
./scripts/run-e2e.ps1
./scripts/run-load-test.ps1 -VirtualUsers 500 -RampSeconds 30 `
  -DurationSeconds 120 -ThinkTimeMs 1000 -TimeoutSeconds 600
./scripts/verify-backup-restore.ps1
```

Workflow release phải tạo mười image, SBOM SPDX bằng Syft 1.50.0 đã pin và quét Gitleaks/Trivy. Policy image hiện chặn phát
hiện `HIGH`/`CRITICAL`, secret và misconfiguration tương ứng; report và image digest phải được lưu cùng
commit phát hành. Không dùng kết quả quét cũ để duyệt một image mới.

`trivy-action` phải dùng commit an toàn `57a97c7e7821a5776cebc9bb87c984fa69cba8f1` và khóa binary
immutable `v0.69.3`; không dùng action SHA đã bị gỡ hoặc `latest`. Redis và Prometheus production phải
dùng đúng digest đã scan trong `compose.yaml`. Không thêm allowlist chỉ để mở gate phát hành.

MySQL được build thành image first-party từ đúng MySQL 8.4.11 digest. Image thay gosu 1.19 bằng binary
build tại commit `6456aaa0f3c854d199d0f037f068eb97515b7513`, Go 1.26.5 và `x/sys` 0.45.0; đồng thời loại MySQL
Shell không được server/entrypoint sử dụng. Image MySQL phải đi qua Trivy/SBOM/attestation gate và
Flyway/MySQL integration như các image first-party khác. Image backup dùng cùng base digest nhưng tiếp
tục loại gosu/mysqlsh vì chỉ cần client backup/restore.

Pushgateway được build thành image first-party từ tag `v1.11.3`, commit
`e803ebd81be5867ff17a21205030611fa033af13`, bằng Go 1.26.5 và `x/text` 0.39.0. Runtime layer vẫn dùng
đúng upstream digest để giữ `wget` cho healthcheck, nhưng binary `/bin/pushgateway` phải được thay bằng
binary vừa build. Image này phải đi qua cùng Trivy/SBOM/attestation gate như chín image first-party còn lại.
Volume persistence được mount tại `/pushgateway` và ghi `/pushgateway/metrics.db` để giữ UID 65534;
không mount vào `/data` vì volume mới tại đó có owner `root:root` và làm persistence fail dù healthcheck
vẫn xanh. Với volume development cũ được tạo trước thay đổi này, dừng stack và xóa riêng volume
`pushgateway-data` trước khi khởi động lại; không áp dụng thao tác đó cho volume khác.

Grafana được build thành image first-party từ đúng tag `v13.1.3`, commit
`45a27d64b64a82d666b06aa5c5bb3521587edb0d` và Go 1.26.5. Patch chỉ bỏ backend Tempo không dùng;
runtime bỏ Tempo, Elasticsearch và Zipkin, tắt plugin preinstall mặc định và xóa đúng ba thư mục này
khỏi volume plugin cũ trước khi chạy. Novel Plus chỉ provision Prometheus. Image phải chạy UID 472,
không được tải plugin lúc khởi động và phải qua scan, SBOM, attestation cùng smoke dashboard trước RC.

Job `runtime-dependencies` phải quét đúng hai reference có digest của Redis và
Prometheus trước khi build/push image first-party. Mỗi report JSON phải có đúng
`ArtifactName`, schema Trivy 2, không còn `Vulnerabilities`, `Secrets` hoặc `Misconfigurations`, được ký
provenance và lưu trong `release-evidence`. Bộ `verify-runtime-reports` phải nhận đúng hai file, không
thiếu/thừa. Workflow promotion tải các report này vào thư mục riêng, xác minh lại nội dung và provenance;
không trộn chúng với 40 artifact của manifest image. Nếu một dependency còn finding, RC phải dừng trước
khi đẩy image.

Chỉ tag dạng `v<phiên-bản>` mới được phép chạy job đẩy image. `workflow_dispatch` từ branch chỉ chạy
các gate kiểm tra, không được đẩy image hoặc tạo GitHub Release. Workflow RC chỉ đẩy tag phiên bản,
không tạo Docker tag `latest`. Sau khi mười image được đẩy và
attest thành công, workflow tổng hợp `release-manifest.json` và `release-manifest.sha256`. Manifest
phải khóa đúng tag, SHA commit, GitHub Actions run, mười subject image, registry digest, hash của report
Trivy/SBOM/digest/attestation, cùng ID và URL provenance/SBOM attestation. Thiếu một image, sai checksum,
artifact bị thay đổi hoặc tag/commit không khớp phải chặn job GitHub Release.

Sau khi khóa manifest, job `release-smoke` phải sinh `.env.rc-smoke` bằng lệnh `candidate-env`, kéo và
khởi động image theo đúng `subject@sha256` với `--no-build`, rồi kiểm tra Flyway, health, CSP, CSRF,
Prometheus/Grafana, biên Caddy, backup mã hóa và restore drill bằng đúng image backup RC. Job phải ghi
`rc-compose-smoke.json`, ký provenance cho biên bản này
và đính kèm cả JSON lẫn log vào draft. Không được tạo draft nếu stack digest không qua smoke.

Kiểm tra nhánh dương và các nhánh fail-closed của bộ sinh manifest trước khi tạo tag:

```bash
node --test scripts/generate-release-manifest.test.mjs
```

GitHub Release RC phải ở trạng thái draft và đính kèm toàn bộ artifact `release-evidence`. Chỉ chạy
workflow `Quảng bá release production` sau khi biên bản ở trạng thái GO và đã cấu hình required
reviewers cho GitHub Environment `production`. Người chạy phải nhập mã cùng SHA-256 của biên bản đã
ký. Workflow promotion tải lại asset từ draft, xác minh manifest theo tag/commit, kiểm tra online cả
provenance lẫn SBOM attestation của mười OCI image, đồng thời xác minh provenance/nội dung biên bản
runtime RC khớp tag, commit và hash manifest. Hai report dependency runtime phải được tách khỏi artifact
manifest, kiểm tra lại đúng digest/không finding và xác minh provenance từng report. Sau đó workflow mới ghi
`production-promotion-record.json`, rồi mới
publish và đánh dấu GitHub Release là latest. Khi triển khai hoặc rollback, luôn dùng digest trong
manifest đã ký duyệt; hệ thống không phát hành Docker tag `latest`.

Trước khi bật production, đối chiếu và ký
[biên bản sẵn sàng production](production-readiness-acceptance.md). Tối thiểu phải có smoke VNPAY
one-off, VNPAY Recurring và VietQR bằng credential thật; phê duyệt KYC/dữ liệu cá nhân, bản quyền,
thuế/chứng từ, recurring consent, refund và payout bốn mắt. Xem [hướng dẫn VNPAY](vnpay.md) trước khi
bật thanh toán sandbox hoặc production.
