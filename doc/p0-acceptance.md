# Nghiệm thu P0

Ngày nghiệm thu kỹ thuật: **27/07/2026**.

Lần tái xác minh trên mã nguồn hiện tại: **27/07/2026**.

P0 đã hoàn tất ở phạm vi mã nguồn, schema, migration, kiểm thử và đóng gói. Trạng thái này không đồng nghĩa hệ thống đã được phép bật thanh toán hoặc phát hành chứng từ trong production; các cổng thật, quy trình KYC và nghĩa vụ pháp lý vẫn phải được đơn vị vận hành phê duyệt.

## Phạm vi đã hoàn tất

| Nhóm | Kết quả |
|---|---|
| Ví và sổ cái | Ví độc giả, ví doanh thu tác giả, sổ cái kép bất biến, idempotency, projection tương thích và lịch sử biến động có thể kiểm toán |
| Thanh toán Việt Nam | VNPAY có IPN/Return/QueryDr, VietQR có QR và webhook xác thực, adapter payout NAPAS fail-closed khi chưa có hợp đồng thật |
| Hoàn tiền và đối soát | State machine refund, chargeback, clearing, debt recovery, audit bất biến và nhập lô đối soát ngân hàng |
| Tài chính tác giả | KYC mã hóa, chu kỳ/mức tối thiểu rút, hold/payout, kiểm soát quyền PII, báo cáo doanh thu, thuế và chứng từ kỹ thuật |
| Bản quyền | Báo cáo vi phạm, gỡ nội dung, kháng nghị, bằng chứng sở hữu và lịch sử phiên bản chương |
| Kiểm duyệt | Queue riêng cho truyện, chương, bìa và bình luận; từ khóa nhạy cảm, phát hiện trùng, phân loại tuổi |
| Bảo mật vận hành | Audit log bất biến, rate limit và 2FA cho các luồng đã tích hợp |
| Đóng gói | JDK 21, image chạy non-root, Compose có MySQL/Redis/migration/front/crawl/admin, healthcheck và volume bền vững |

Ảnh bìa tồn tại trước `20260726_cover_moderation.sql` được giữ ở trạng thái đã duyệt. Ảnh bìa mới, ảnh AI mới hoặc ảnh do tác giả thay lại được đưa về trạng thái chờ duyệt; thao tác approve/reject của admin chuyển trạng thái thật và reject lưu lý do.

## Bằng chứng nghiệm thu

- Maven reactor bằng JDK 21: **255 test**, gồm 247 đạt và 8 integration có điều kiện được skip trong lượt mặc định; không có failure/error. Tám ca bị skip gồm integration MySQL của P0, editor P1, analytics P1, cộng tác tác giả P1, tìm kiếm tiếng Việt P1, recommendation P1 và hai ca thông báo chương mới P1; từng nhóm được bật riêng bằng cờ hệ thống tương ứng.
- Integration P0 trên MySQL 8.4 thật: `RefundMySqlIntegrationTest` đạt 1/1, không skip; sau test không còn user, order, refund, wallet hoặc ledger fixture `990000...`.
- Migration P0 và migration bìa chạy lại hai lần trên database mới mà không lỗi; 20 bảng P0 và 12 trigger bất biến được đối chiếu trực tiếp sau lượt chạy lại, marker backfill và index queue giữ đúng số lượng.
- `scripts/verify-i18n.mjs` đối chiếu catalog `75/640/149/551` key cho common/front/crawl/admin, parse 180 tệp JavaScript, 333 khối JavaScript inline và kiểm tra 275 root HTML thành công.
- Docker build dùng profile `central-repo` đã được đối chiếu bằng effective POM; profile ghi đè repository dependency/plugin sang Maven Central và build đủ reactor thành công. Bốn image `mysql`, `front`, `crawl`, `admin` build thành công; ba ứng dụng chạy với UID/GID `10001`, healthcheck đạt và HTTP smoke trả 200 theo đúng cơ chế xác thực.
- Green, orange, dark và blue đều qua smoke mobile ở viewport 320 px, không tràn ngang và không có console error; blue lấy template mobile từ runtime fallback. Green cũng đạt desktop và viewport 390 px.
- Anonymous không thấy truyện chờ duyệt hoặc 18+ trên trang chủ, ranking, search và URL trực tiếp. Tài khoản đã xác minh tuổi được phép xem truyện 18+ nhưng vẫn bị chặn truyện chờ duyệt. Fixture recommendation `996000...` đã được xóa sạch sau smoke.
- Trang front, nạp Xu, admin và crawler không còn nút/chuỗi Alipay first-party.

Lệnh kiểm tra chính:

```powershell
$env:JAVA_HOME = 'C:\duong-dan\toi\jdk-21'
mvn clean test
node scripts/verify-i18n.mjs
docker compose config --quiet
docker compose up -d --build
docker compose ps --all
git diff --check
```

Test integration P0 phải chạy trên database cô lập đã áp dụng đủ migration. Có thể truyền datasource trực tiếp để không phụ thuộc file ShardingSphere của máy phát triển:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3306/novel_plus'
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'com.mysql.cj.jdbc.Driver'
$env:SPRING_DATASOURCE_USERNAME = 'root'
$env:SPRING_DATASOURCE_PASSWORD = '<mat-khau-db-test>'
mvn -Dp0.mysql.it=true -Dtest=RefundMySqlIntegrationTest `
  -Dsurefire.failIfNoSpecifiedTests=false -pl novel-front -am test
```

## Cổng production còn phải mở bằng quy trình vận hành

- Nhận credential merchant, URL/IP callback và đối soát sandbox/production từ VNPAY.
- Cấu hình tài khoản nhận tiền và webhook secret thật cho VietQR.
- Ký hợp đồng/API payout với ngân hàng hoặc NAPAS; adapter hiện không giả lập giao dịch thành công.
- Phê duyệt quy trình KYC, bốn mắt, chuyển khoản, đối soát và xử lý khiếu nại.
- Tham vấn pháp lý tại Việt Nam về bản quyền, dữ liệu cá nhân, trung gian thanh toán, thuế và chứng từ/hóa đơn điện tử.
- MoMo và ZaloPay được hoãn sang giai đoạn tích hợp sau; đây không phải tiêu chí chặn P0 đã chốt.

Xem thêm [ví và sổ cái](wallet-ledger.md), [VNPAY](vnpay.md), [tài chính tác giả](author-finance.md) và [runbook triển khai](deployment.md).
