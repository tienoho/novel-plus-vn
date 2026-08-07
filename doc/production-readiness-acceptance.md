# Biên bản sẵn sàng production Novel Plus

Ngày tái xác minh kỹ thuật: **02/08/2026**.

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
| Full Maven reactor | `mvn -B -ntp verify -Pcentral-repo` trong image Maven 3.9.11 + Eclipse Temurin 21; cả `novel-common`, `novel-front`, `novel-crawl`, `novel-admin` đều `SUCCESS` | Đạt |
| Flyway/MySQL | Image Flyway 13.1.0 chỉ giữ plugin/driver cần cho MySQL; 36 migration migrate/validate thành công trên MySQL 8.4 và lần chạy thứ hai không áp dụng lại | Đạt |
| Concurrency nghiệp vụ | Gamification, kỳ đặc biệt, quest, thuê bao checkout/review và gift-code chạy integration/concurrency trên MySQL 8.4 | Đạt trong môi trường test |
| VNPAY one-off Sandbox | Credential Sandbox được VNPAY chấp nhận; giao dịch NCB/OTP trả `00`; payload ký thật settlement IPN local `00`, replay `02`, cộng đúng 1.000 Xu và ledger zero-sum | Đạt application/provider Sandbox; chưa đạt server-delivered IPN |
| E2E bốn theme | Green: 22 đạt, 3 bỏ qua có chủ đích; orange/dark/blue: mỗi theme 19 đạt, 6 bỏ qua có chủ đích. Luồng backoffice/tác giả production-like chỉ chạy một lần trên green desktop | Đạt |
| Load 500 VU | 53.218 request; 0 lỗi; workload 70% đọc, 20% khám phá, 10% ghi; p95 lần lượt 64,63 ms, 19,54 ms và 27,12 ms | Đạt SLO |
| Toàn vẹn sau tải | 5.321/5.321 thao tác ghi khớp delta DB; không lệch ledger/projection/idempotency; Hikari peak 7/10, pending 0, timeout 0 | Đạt |
| Backup/restore | Backup GPG AES-256, checksum hai payload đạt; restore drill sang database mới thành công với 142 bảng | Đạt |
| Image security | Front/admin/crawl/migrations được quét Trivy 0.68.2 với scanner `vuln,secret,misconfig`, mức `HIGH,CRITICAL` và `--ignore-unfixed`; không có phát hiện thuộc cấu hình quét | Đạt theo policy hiện tại |
| SBOM | Bốn SBOM SPDX JSON đã được tạo cho front/admin/crawl/migrations | Đạt |
| Secret scan | Gitleaks quét 45,35 MB, không phát hiện leak; allowlist chỉ chứa artifact/thư viện ngoài và fixture test có lý do | Đạt |
| i18n/CSP/JS | 3.246 file text; 586 ngoại lệ hợp lệ; 230 JS và 230 inline template parse; 280 HTML root; 230 script nonce-covered; catalog common/front/crawl/admin parity | Đạt |
| Chất lượng diff/cấu hình | `git diff --check`, parse Compose, YAML, Node và PowerShell đều trả thành công | Đạt |

Các artifact sinh cục bộ nằm trong `e2e/test-results/` và bị loại khỏi Git. Kết quả trên phải được
tạo lại trong CI/release chính thức; không lấy việc artifact đang tồn tại trên máy phát triển làm
bằng chứng phát hành lâu dài.

## Giới hạn của bằng chứng

- Full reactor đã chạy bằng JDK 21 trong Docker. Lượt integration/concurrency MySQL chạy trực tiếp
  trên máy phát triển dùng JDK 17 vì host chưa có JDK 21; pipeline CI đã cấu hình JDK 21 và phải chạy
  lại gate này trước release.
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

Chỉ khi toàn bộ checklist này có bằng chứng và người có thẩm quyền ký duyệt mới đổi trạng thái thành
**production-approved**. Xem thêm [runbook triển khai](deployment.md), [VNPAY](vnpay.md),
[ví và sổ cái](wallet-ledger.md), [tài chính tác giả](author-finance.md) và
[nghiệm thu P0](p0-acceptance.md).
