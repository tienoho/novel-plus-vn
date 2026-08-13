# Biên bản sẵn sàng production Novel Plus

Ngày tái xác minh kỹ thuật: **10/08/2026**. Cập nhật audit secret lịch sử: **09/08/2026**.

## Kết luận

Phần kỹ thuật có thể tự xác minh trong môi trường local/Docker Compose một máy đã đạt các gate hiện
có cho mục tiêu 500 người dùng đồng thời. Trạng thái hiện tại là **kỹ thuật local/Compose đạt, chưa
được phê duyệt production**.

Không được dùng biên bản này để tuyên bố hệ thống đã sẵn sàng nhận tiền hoặc dữ liệu production.
Việc phát hành vẫn bị chặn bởi smoke với credential thật của nhà cung cấp và các phê duyệt
pháp lý/vận hành ở cuối tài liệu.

## Ma trận nghiệm thu kỹ thuật

| Hạng mục | Bằng chứng tái xác minh | Kết quả |
|---|---|---|
| Full Maven reactor | `mvn -B -ntp clean verify -Pcentral-repo` bằng Temurin 21.0.11 và Maven 3.9.11; common 242, front 456, crawl 8, admin 63 test, không failure/error; front skip 48 integration test đúng profile mặc định | Đạt |
| Flyway/MySQL | Image Flyway 13.1.0 chỉ giữ plugin/driver cần cho MySQL; 40 migration migrate/validate thành công trên MySQL 8.4 và lần chạy thứ hai không áp dụng lại | Đạt |
| Concurrency nghiệp vụ | 24 lớp/62 test cho gamification, kỳ đặc biệt, quest, thuê bao, gift-code và payout bốn mắt chạy trên MySQL 8.4; actor duyệt không thể execute, hai executor cạnh tranh chỉ một người claim | Đạt trong môi trường test |
| VNPAY one-off Sandbox | Credential Sandbox được VNPAY chấp nhận; giao dịch NCB/OTP trả `00`; payload ký thật settlement IPN local `00`, replay `02`, cộng đúng 1.000 Xu và ledger zero-sum | Đạt application/provider Sandbox; chưa đạt server-delivered IPN |
| Bộ UAT VNPAY công khai | `prepare/start/preflight/preflight-vnpay-checkout/test-vnpay-uat.ps1` tạo cấu hình ngoài Git, lưu merchant secret và dữ liệu thẻ Sandbox trong file secret ignored, kiểm tra HTTPS/IPN fail-closed và checkout đúng merchant/amount/Return URL, rồi chạy Playwright bằng container đã pin; checkout-preflight cô lập gần nhất đạt trong 106,3 giây với đơn pending, số dư/ledger không đổi | Sẵn sàng chạy sau khi DNS/IPN URL được cấu hình; chưa thay thế SIT thật |
| E2E bốn theme | Green: 22 đạt, 3 bỏ qua có chủ đích; orange/dark/blue: mỗi theme 19 đạt, 6 bỏ qua có chủ đích. Luồng backoffice/tác giả production-like chỉ chạy một lần trên green desktop | Đạt |
| Load 500 VU | 52.728 request; 0 lỗi; workload 70% đọc, 20% khám phá, 10% ghi; p95 lần lượt 121,22 ms, 22,89 ms và 27,9 ms | Đạt SLO |
| Toàn vẹn sau tải | 5.272/5.272 thao tác ghi khớp delta DB; không lệch ledger/projection/idempotency; Hikari peak 7/10, pending 0, timeout 0 | Đạt |
| Backup/restore | Backup GPG AES-256, checksum `database.sql` và `files.tar.gz` đạt; restore drill sau 40 migration thành công với 143 bảng | Đạt |
| Image security | Bảy image front/admin/crawl/migrations/caddy/backup/alertmanager được quét local lần gần nhất bằng Trivy 0.68.2 với scanner `vuln,secret,misconfig`, mức `HIGH,CRITICAL` và `--ignore-unfixed`; không có phát hiện thuộc policy. Pushgateway, MySQL và Grafana hardened được Trivy 0.69.3 nhận diện đúng Go/OS target và cùng trả 0 vulnerability/secret/misconfiguration. CI/release khóa action an toàn `57a97c7e…` và binary immutable `v0.69.3` | Mười image đạt local; chờ RC |
| Dependency image runtime | Hai dependency còn lại đã pin digest: Redis 7-alpine và Prometheus 3.13.2 scan sạch. Pushgateway, MySQL và Grafana đã chuyển thành image first-party hardened | Đạt local; chờ report/provenance RC |
| SBOM | Bảy SBOM SPDX 2.3 có package và đúng một `DESCRIBES`; ba image bổ sung caddy/backup/alertmanager lần lượt có 180/790, 118/3.611 và 232/691 package/relationship | Đạt |
| Release/promotion | RC chỉ push từ tag `v*`, không tạo Docker `latest`, quét hai dependency theo digest, tổng hợp manifest mười image, smoke đúng tập `subject@sha256` với `--no-build`, gồm backup mã hóa/restore drill bằng image backup RC, rồi ký provenance cho report dependency và `rc-compose-smoke.json` trước khi tạo draft. Promotion yêu cầu Environment `production`, mã/hash biên bản, verify manifest, hai report dependency, biên bản runtime và provenance/SPDX attestation trước khi publish | Chưa cấu hình reviewer/chạy RC thật |
| Deploy theo digest | Compose nhận mười biến image đầy đủ; `deploy-env` chỉ sinh chúng sau khi manifest, artifact, checksum và promotion record khớp tag/commit/hash biên bản; runbook production dùng pull + `--no-build` | Đạt test cục bộ; chưa diễn tập trên server production |
| Secret scan | Gitleaks được ghim `8.30.0` và có positive control fail-closed. Snapshot hiện tại quét 45,63 MB không có leak. Full-history scan 577 commit/59,07 MB đã loại đúng hai JWT mẫu theo fingerprint nhưng vẫn phát hiện hai khóa riêng merchant Alipay lịch sử; workflow release chặn trước bước đẩy image | **Blocker release** |
| i18n/CSP/JS | 3.276 file text; 584 file có ngoại lệ hợp lệ; 231 JS và 231 inline template parse; 280 HTML root; 231 script nonce-covered; catalog common/front/crawl/admin parity | Đạt |
| Chất lượng diff/cấu hình | `git diff --check`, Compose, Actionlint, Node và 15 script PowerShell đều trả thành công | Đạt |

Các artifact sinh cục bộ nằm trong `e2e/test-results/` và bị loại khỏi Git. Kết quả trên phải được
tạo lại trong CI/release chính thức; không lấy việc artifact đang tồn tại trên máy phát triển làm
bằng chứng phát hành lâu dài.

## Tái xác minh patch bảo mật và đóng gói ngày 08/08/2026

- Full-history Gitleaks quét 577 commit: hai JWT mẫu trong tài liệu cũ được ignore bằng đúng
  fingerprint; hai khóa riêng merchant Alipay vẫn bị phát hiện và tiếp tục chặn release. Scan trực
  tiếp sáu file thay đổi không phát hiện secret.
- Workflow release đã thêm full-history Gitleaks trước job đẩy image và toàn bộ action bên ngoài đã
  được pin SHA 40 ký tự. YAML parse thành công.
- Trước khi sửa đóng gói, thread dump xác nhận Ant đang random-read ZIP distribution 188 MB của lần
  build cũ tại `Zip.selectOutOfDateResources`. Execution `package-distribution` hiện xóa toàn bộ
  `target/build` trước mọi copy/zip; test invariant đạt 7/7.
- Package không `clean` trên output cũ đạt `BUILD SUCCESS`; gọi trực tiếp execution lần hai tiếp tục
  xóa output và đạt `BUILD SUCCESS`. ZIP cuối có 3.424 entry, không entry trùng, đủ jar, Dockerfile,
  script và runtime/overlay của green, orange, dark, blue.
- Test JDK 21 được chạy lại theo module: common 239 test đạt; front 432 test không có failure/error
  (42 integration test skip theo profile), sau đó test packaging thay đổi đạt 7/7; crawl 7/7; admin
  57/57. Đây là bằng chứng module và targeted test, không được ghi nhận như một lần full-reactor duy
  nhất sau patch.
- Verifier MySQL hiện fail-fast nếu Maven không dùng Java 21. Nhánh âm đã chặn đúng Java 17 trước khi
  dựng database; lượt đầy đủ bằng Temurin 21 migrate Flyway hai lần và chạy 9 lớp với tổng 37 test
  integration/concurrency, không failure/error/skip. Sau lượt chạy còn 0 container, 0 volume và không
  còn secret tạm.
- State machine VNPAY Recurring đã bổ sung QueryDr theo đặc tả provider. Unit contract kiểm tra request,
  checksum và mapping trạng thái; MySQL concurrency chứng minh hai worker chỉ một worker claim được
  cycle `PROVIDER_PENDING`, fallback chỉ được mở sau kết quả thất bại cuối cùng đã xác thực, và replay
  settlement không gia hạn cycle lần hai.
- Luồng hủy thuê bao đã chuyển mandate sang outbox `REVOKE_PENDING` trong cùng transaction, gọi
  `cancel_recurring` bằng worker có lease/retry và chỉ xóa ciphertext token sau trạng thái provider
  mong muốn (`00/04/12`). Unit contract kiểm tra checksum/mapping; MySQL integration chứng minh hai
  worker chỉ một worker claim, replay không thể thu hồi lần hai và token/open slot được xóa đúng.
- Lượt xác minh Java 21 sau hàng đợi thu hồi/admin: common 242/242; front 441 test, 0 failure/error,
  43 integration skip đúng profile mặc định; admin 57/57. Đây là mốc lịch sử trước migration
  idempotency mandate.
- Ngày 09/08/2026, Flyway validate/migrate 39 migration hai lượt trên MySQL 8.4; 9 lớp
  integration/concurrency đạt 40/40 và `ReadingSubscriptionMySqlIntegrationTest` đạt 7/7. Test mới
  chứng minh snapshot request mandate không sửa được, `clientRequestId` duy nhất và ciphertext
  `dataKey` bị xóa khi mandate thất bại. Nhóm unit/controller/catalog/packaging liên quan đạt 31/31.
- Full reactor sau các thay đổi đạt trên Temurin 21: common 242/242, front 453 test không
  failure/error với 45 integration skip đúng profile mặc định, crawl 7/7 và admin 57/57. Sau khi sửa
  POST mở chương gửi CSRF token, `CsrfFilterTest` và `ReadingTicketUiPackagingTest` đạt 7/7.
- Frontend thuê bao desktop/mobile đã nối đủ tạo/truy vấn mandate, checkout auto-renew, đổi nguồn,
  chấp nhận giá và hủy cuối kỳ; redirect sang VNPAY Recurring dùng HTML form `POST` với đúng
  `ispTxnId`, `tmnCode`, `dataKey`. UAT one-off vẫn chủ động tắt Recurring khi chưa có credential riêng.
- Admin đã có read model mandate không chứa ciphertext, bộ lọc trạng thái, audit và retry bắt buộc
  reason/version. MySQL chứng minh admin không cướp lease đang hoạt động, audit không sửa được và
  worker chỉ claim lại sau khi admin đặt lịch hợp lệ.
- Queue thu hồi mandate được xuất qua metric bounded-label
  `novel_subscription_renewal_queue{state="mandate_revoke_pending"}` và cảnh báo sau 15 phút tồn đọng;
  unit test chứng minh gauge vẫn cập nhật khi không có mandate đến hạn trong lượt quét.
  `promtool` của Prometheus v3.11.3 xác nhận toàn bộ 13 alert rule hợp lệ.
- `verify-gamification.ps1 -ConfigOnly`, POM/YAML parse, i18n, CSP và `git diff --check` đều đạt.
- Ngày 09/08/2026, full reactor được chạy lại sau patch client security/UAT: common 242/242, front
  443 test với 45 integration skip đúng profile, crawl 7/7 và admin 59/59. Admin packaging chứng minh
  dictionary không còn ghép HTML/eval và decoder không còn dùng `innerHTML` với input động.
- Checkout-preflight VNPAY được thực thi trên stack Docker cô lập với merchant/secret ngẫu nhiên:
  đăng nhập và tạo URL Sandbox đúng merchant, mệnh giá, Return URL; đơn giữ `pending`, số dư không đổi,
  chưa có ledger. Runner tự dọn container, volume và secret tạm.
- E2E green build lại toàn bộ image đạt 22 test, 3 skip có chủ đích trên desktop/mobile trong 29,3 giây.
  Sau chạy không còn `e2e/.auth`, container hoặc volume của project E2E.
- Bộ prepare UAT đã được chạy với fixture credential giả: tạo đủ `.env.uat`, nội dung đăng ký và năm
  file secret VNPAY; kiểm tra âm xác nhận checksum, số thẻ và OTP không xuất hiện trong `.env` hoặc
  nội dung gửi nhà cung cấp. Compose UAT validate thành công. `test-vnpay-uat.ps1` hiện tự đọc thẻ/OTP
  từ file secret và chỉ dùng process environment khi cần ghi đè tạm thời.
- Regression image JDK 21 gần nhất đạt trên cả bốn theme: green 22 đạt/3 skip; orange, dark và blue
  mỗi theme 19 đạt/6 skip, không có failure. Checkout-preflight cô lập sau thay đổi đạt trong 106,3
  giây và tự dọn toàn bộ container, volume, secret tạm.
- Lượt chốt ngày 09/08/2026 chạy tuần tự green, orange, dark và blue trên database/Flyway sạch đạt lại đúng
  22/3 và 19/6 như trên. Sau khi đồng bộ hai template soạn thảo runtime với overlay theme, full reactor
  bằng Temurin 21.0.11 đạt common 242/242, front 447 test không failure/error với 45 integration skip,
  crawl 7/7 và admin 59/59; Gitleaks snapshot quét 45,55 MB không phát hiện secret.
- Lượt tái xác minh cuối ngày 09/08/2026 trên worktree hiện tại chạy `clean verify` bằng Temurin 21.0.11:
  common 242/242, front 448 test với 45 integration skip, crawl 8/8 và admin 60/60, không
  failure/error. Flyway migrate/validate 39 migration hai lượt; 9 lớp MySQL/concurrency đạt và tự dọn
  container, volume, secret tạm.
- Smoke Compose build lại front/admin/crawl/migrations/Caddy/Alertmanager từ source hiện tại; ba ứng
  dụng healthy, CSP enforce có nonce, crawler CSRF fail-closed, 4 target Prometheus up, metric Flyway
  bằng 1, dashboard Grafana được provision và Caddy chặn actuator trên ba domain ứng dụng.
- Lượt smoke sạch ngày 10/08/2026 dùng toàn bộ image local đã build và volume mới: Flyway thoát mã 0;
  front/admin/crawl/Grafana/Alertmanager/Pushgateway/Caddy đều healthy; CSP nonce, crawler CSRF,
  4 target Prometheus, metric migration, dashboard Grafana và biên Caddy đều đạt. Script đã tự dọn sạch
  container và volume của project sau khi hoàn tất.
- E2E build lại stack và chạy đủ green 22 đạt/3 skip, orange/dark/blue mỗi theme 19 đạt/6 skip trên
  desktop/mobile. Bốn `.last-run.json` đều `passed`, không có test thất bại.
- Bốn image vừa build được quét lại bằng Trivy 0.68.2 với `vuln,secret,misconfig`, mức
  `HIGH,CRITICAL`, `--ignore-unfixed`: front/admin/crawl/migrations đều 0 vulnerability, 0 secret và
  0 misconfiguration theo policy. Gitleaks snapshot hiện tại tiếp tục quét 45,55 MB và không có leak.
- Workflow release hiện pin action Trivy an toàn cùng binary immutable `v0.69.3`, build image cục bộ,
  quét fail-closed và tạo SBOM trước khi đăng nhập/push;
  report Trivy cùng digest được lưu 90 ngày. Actionlint 1.7.12 xác nhận workflow CI/release hợp lệ và
  mọi action bên ngoài vẫn pin SHA đầy đủ. Gate full-history Gitleaks tiếp tục chạy trước job image.
- Full-history Gitleaks được chạy lại trên 577 commit/59,07 MB với report redact: exit code 17 và đúng
  2 finding `generic-api-key` tại hai commit lịch sử của `application-alipay.yml`. Không có finding ở
  snapshot hiện tại; release tiếp tục bị chặn cho tới khi có bằng chứng khóa cũ đã thu hồi/rotate và
  phương án xử lý lịch sử được phê duyệt.
- Load test được chạy lại trên image hiện tại với 500 VU, ramp 30 giây và giữ tải 120 giây: 52.728
  request, 0 lỗi, workload 70/20/10; p95 đọc/khám phá/ghi là 121,22/22,89/27,9 ms. Cả 5.272 thao tác
  ghi khớp delta database, không lệch ledger/projection/idempotency; Hikari peak 7/10, pending và
  timeout đều bằng 0. Runner tự dọn toàn bộ project/volume/secret tạm.
- Backup/restore được chạy lại trên database sạch đã áp dụng và validate đủ 39 migration. Archive GPG
  AES-256 có checksum hợp lệ cho `database.sql` và `files.tar.gz`; restore drill sang database mới
  thành công với 143 bảng. Container, volume, archive và secret tạm được dọn sau khi kiểm tra.
- Ngày 09/08/2026, migration `20260817_author_payout_four_eyes.sql` tách `approved_by`/`executed_by`,
  tách quyền approve/execute và thêm check constraint maker-checker. Flyway migrate/validate 40 migration
  hai lượt; 24 lớp MySQL/concurrency đạt 62/62, trong đó ba test payout chứng minh cùng actor bị chặn,
  bypass service vẫn bị constraint chặn và hai executor cạnh tranh chỉ một người claim. Full reactor
  JDK 21 đạt common 242, front 456 (48 integration skip đúng profile), crawl 8, admin 63. i18n/CSP
  đạt 231 JS/inline/nonce. Đây chưa thay thế diễn tập hai người thật và đối soát sao kê/provider.
- Restore drill sau migration payout tạo archive GPG AES-256, xác minh `database.sql`/`files.tar.gz`
  và phục hồi thành công 143 bảng trên database mới; container, volume, archive và secret tạm đều được dọn.
- E2E green build lại image hiện tại và đạt 22 test, 3 skip có chủ đích, 0 failure trên desktop/mobile;
  sau chạy không còn container, volume hoặc `e2e/.auth`. Orange/dark/blue không chạy lại vì patch chỉ
  thay admin dùng chung, không thay overlay theme; kết quả bốn theme trước patch vẫn là bằng chứng lịch sử.
- Sau khi phát hiện Gitleaks 8.30.1 có thể silent-pass với default rules, workflow CI/release đã ghim
  `GITLEAKS_VERSION=8.30.0`, ghim image positive-control theo digest và yêu cầu fixture tổng hợp phải
  trả exit code 17 trước khi chạy action. Test đóng gói mục tiêu, PyYAML và Actionlint 1.7.12 đều đạt.
  Positive control thực tế phát hiện một leak; snapshot 45,63 MB trả 0 finding; full-history 577 commit
  trả đúng hai finding `generic-api-key` đã biết trong `application-alipay.yml`. Hai finding lịch sử
  vẫn là blocker cho tới khi có bằng chứng revoke/rotate; chúng không được thêm vào allowlist.
- Bốn image front/admin/crawl/migrations tiếp tục không có finding theo policy Trivy hiện tại. SBOM
  SPDX 2.3 đã parse trong bộ nhớ với số package/relationship lần lượt là 469/1.026, 449/986, 429/946
  và 425/936; mỗi tài liệu có đúng một quan hệ `DESCRIBES`. Đây là bằng chứng cục bộ, chưa thay thế
  artifact, digest, attestation và SBOM được sinh lại trên release candidate chính thức.
- Release workflow hiện chặn job push khi ref không phải tag `v*`, nên manual dispatch từ branch không
  thể đẩy image. RC chỉ dùng tag phiên bản, không tạo Docker `latest`. Sau mười job image, job manifest
  tải đúng 40 artifact từ mười job image, kiểm tra SPDX 2.3,
  `DESCRIBES`, digest, subject và URL/ID attestation, rồi khóa tag/commit/run cùng SHA-256 của từng file.
  GitHub Release chỉ chạy sau verifier, ở trạng thái draft và đính kèm toàn bộ `release-evidence`.
  Bộ Node test bao phủ tạo/
  verify thành công và fail-closed khi report bị sửa, thiếu một image hoặc dùng tag branch. Gate này
  chưa được ghi là bằng chứng RC cho tới khi chạy thật trên tag phát hành và lưu artifact/attestation.
- Workflow promotion production chạy thủ công trong GitHub Environment `production`, bắt buộc mã và
  SHA-256 biên bản đã ký, tải lại asset từ draft, xác minh tag/commit/checksum và gọi
  `gh attestation verify` cho cả provenance lẫn predicate SPDX 2.3 của mười image. Chỉ sau đó workflow
  mới ghi promotion record, publish draft và đánh dấu GitHub Release latest. Cần cấu hình required
  reviewers trong GitHub trước khi gate này có hiệu lực vận hành; source code không thể tự chứng minh
  cấu hình reviewer bên ngoài repo.
- Compose hiện cho phép override toàn bộ mười image first-party bằng đầy đủ `subject@sha256`. Lệnh
  `deploy-env` chỉ ghi `.env.release` sau khi verify lại 40 artifact, checksum manifest, tag/commit và
  promotion record khớp hash biên bản/manifest. Runbook production đã chuyển sang pull mười digest và
  `up --no-build`; build source trực tiếp chỉ còn dành cho development/UAT. Backup, restore và
  restore-drill dùng chung một digest backup đã attest.
- Image Alertmanager upstream `0.32.1` có 48 HIGH fixed-available trong Go binary; đổi lên binary
  upstream `0.33.1` vẫn còn 24. Dockerfile hiện build đúng commit `2c8da51...`, UI asset có checksum,
  Go 1.26.5 cùng `x/crypto 0.53.0`, `x/text 0.39.0`, gRPC 1.82.1. Image kết quả đạt runtime smoke,
  Syft 232 package/691 relationship/1 `DESCRIBES` và Trivy 0 vulnerability/secret/misconfiguration.
- Release hiện có matrix quét đúng hai dependency runtime theo digest và ký provenance cho từng report.
  Verifier từ chối file thiếu/thừa, sai digest hoặc còn finding. Promotion tải report vào thư mục riêng
  để không làm sai bộ 40 artifact của manifest, sau đó xác minh lại nội dung và provenance từng report.
  Theo scan cục bộ hiện tại, gate dependency sẽ đạt với Redis/Prometheus; chưa có RC thật nên chưa có
  report/attestation GitHub dùng làm bằng chứng phát hành.
- Pushgateway `v1.11.3` đã được build từ commit chính thức `e803ebd…` bằng Go 1.26.5 sau khi nâng
  `x/text` 0.39.0 và `x/sync` 0.21.0; toàn bộ test upstream đạt, binary Linux/amd64 chứa đúng metadata
  dependency. Dockerfile production giữ runtime upstream đã pin để tương thích healthcheck `wget` và
  thay đúng binary. Container chạy UID 65534, readiness đạt; Trivy 0.69.3 nhận diện một Go binary và
  trả 0 vulnerability/secret/misconfiguration. Smoke Compose còn phát hiện `/data` root-owned không ghi
  được, nên persistence đã chuyển sang volume `/pushgateway` kế thừa đúng owner `nobody:nobody`.
  Lượt xác minh bằng volume GUID sạch đã ghi metric, tạo `metrics.db` owner 65534, dừng container và
  phục hồi metric trên container thứ hai; Compose isolated sau sửa healthy, ghi volume thành công và tự dọn.
- MySQL 8.4.11 hardened thay gosu upstream Go 1.24.6 bằng gosu 1.19 build từ commit `6456aaa…`, Go
  1.26.5 và `x/sys` 0.45.0; loại bundle MySQL Shell không được entrypoint/server dùng. Container chạy
  mysqld bằng UID 999, query tạo database/bảng/insert đạt, gosu hạ quyền đúng và Trivy 0.69.3 trả 0
  vulnerability/secret/misconfiguration. Sau khi tích hợp image vào Compose, Flyway migrate thành công
  hai lượt và đủ 24 lớp MySQL integration/concurrency đạt 62/62, không failure/error/skip; project kiểm
  thử, container và volume tạm đã được dọn riêng sau khi thu bằng chứng.
- Grafana 13.1.3 hardened được build từ đúng commit `45a27d64…` bằng Go 1.26.5. Trivy 0.69.3 trả 0
  vulnerability/secret/misconfiguration. Smoke standalone xác nhận UID 472, health database `ok`,
  datasource Prometheus và dashboard `novel-plus-overview`; volume đã seed Tempo/Elasticsearch/Zipkin
  được dọn đúng ba thư mục, không còn background preinstall hoặc process plugin. Smoke toàn stack local
  ngày 10/08/2026 tiếp tục xác nhận Grafana healthy và dashboard được provision. Chưa có SBOM/attestation
  RC và chưa có smoke toàn stack bằng tập image RC khóa theo digest.

## Giới hạn của bằng chứng

- Full reactor đã chạy bằng JDK 21 trong Docker. Lượt integration/concurrency MySQL gần nhất chạy bằng
  Temurin 21 trên máy phát triển; pipeline CI cũng cấu hình JDK 21 và verifier tự từ chối JVM khác 21.
- E2E cố ý không lặp các luồng backoffice/tác giả giống nhau trên cả bốn theme; các luồng này chạy trên
  green desktop, còn shell/UI công khai được kiểm tra trên từng theme và viewport.
- Trivy dùng `--ignore-unfixed`; kết quả không thay thế việc rà soát advisory chưa có bản vá hoặc đánh
  giá rủi ro thủ công.
- Load test đo stack Compose cô lập trên máy kiểm thử, không mô phỏng độ trễ nhà cung cấp thanh toán,
  Internet production hoặc lỗi hạ tầng dài hạn.
- Không có PHP first-party trong repo, vì vậy `php -l` không áp dụng.

## Lệnh tái xác minh chính

```powershell
docker run --rm `
  -v "D:\Project\novel-plus:/workspace" `
  -v "$HOME/.m2:/root/.m2" `
  -w /workspace `
  maven:3.9.11-eclipse-temurin-21 `
  mvn -B -ntp verify -Pcentral-repo

./scripts/verify-gamification.ps1 -StopAfter
./scripts/run-e2e.ps1
./scripts/run-load-test.ps1 -VirtualUsers 500 -RampSeconds 30 `
  -DurationSeconds 120 -ThinkTimeMs 1000 -TimeoutSeconds 600 -SkipImageBuild
./scripts/verify-backup-restore.ps1
./scripts/run-vnpay-sandbox-smoke.ps1
node scripts/verify-i18n.mjs
./scripts/verify-csp.ps1 -FailOnBlocker
git diff --check
```

Quy trình image scan, SBOM và các gate CI phải dùng workflow release hiện hành, không sao chép
credential hoặc report generated vào Git.

## Checklist bắt buộc trước khi production-approved

Các mục dưới đây cần credential, hợp đồng hoặc quyền phê duyệt bên ngoài repo và hiện **chưa có bằng
chứng hoàn tất**:

Quyết định, mã bằng chứng, diễn tập và chữ ký cho KYC, thuế, bản quyền, dữ liệu cá nhân, recurring,
refund và payout được quản lý tập trung trong
[biên bản phê duyệt pháp lý và vận hành](legal-operational-approval-record.md). Không chọn các checkbox
bên dưới chỉ vì mẫu biên bản đã tồn tại; mục tương ứng trong biên bản phải có trạng thái phê duyệt còn
hiệu lực và gắn với đúng release candidate.

- [ ] Thu hồi/rotate khóa merchant Alipay từng xuất hiện trong lịch sử Git; sau khi có bằng chứng mới
  được người có thẩm quyền chọn rewrite lịch sử hoặc baseline đúng hai fingerprint còn lại.
- [ ] VNPAY one-off production smoke với merchant, checksum, IPN và QueryDr được phê duyệt.
- [ ] VNPAY Recurring production smoke gồm tạo mandate, charge, trạng thái pending/QueryDr, hủy và
  idempotency bằng credential thật.
- [ ] VietQR production webhook smoke với tài khoản nhận tiền và secret thật.
- [ ] AI provider smoke nếu AI được bật trong bản phát hành.
- [ ] Phê duyệt KYC, bảo vệ dữ liệu cá nhân và thời hạn lưu/xóa dữ liệu.
- [ ] Phê duyệt quy trình bản quyền và tiếp nhận/gỡ bỏ nội dung vi phạm.
- [ ] Phê duyệt thuế, hóa đơn/chứng từ và đối soát doanh thu.
- [ ] Phê duyệt nội dung recurring consent, thông báo đổi giá và bằng chứng người dùng chấp thuận.
- [ ] Phê duyệt chính sách refund/chargeback và SLA xử lý khiếu nại.
- [ ] Diễn tập payout thủ công bốn mắt; payout ngân hàng tự động tiếp tục fail-closed khi chưa có
  adapter/hợp đồng được duyệt.
- [ ] Chạy lại CI/release trên commit phát hành và lưu report, SBOM, digest image cùng biên bản ký duyệt.
- [ ] Cấu hình required reviewers cho GitHub Environment `production`; chạy promotion từ draft và lưu
  `production-promotion-record.json` khớp mã/SHA-256 biên bản đã ký.

Chỉ khi toàn bộ checklist này có bằng chứng và người có thẩm quyền ký duyệt mới đổi trạng thái thành
**production-approved**. Xem thêm [runbook triển khai](deployment.md), [VNPAY](vnpay.md),
[ví và sổ cái](wallet-ledger.md), [tài chính tác giả](author-finance.md) và
[nghiệm thu P0](p0-acceptance.md), cùng
[biên bản phê duyệt pháp lý/vận hành](legal-operational-approval-record.md).
