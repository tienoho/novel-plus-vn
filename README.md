# Novel Plus

<p align="center">
  <a href="https://github.com/201206030/novel-plus"><img alt="GitHub stars" src="https://img.shields.io/github/stars/201206030/novel-plus?logo=github"></a>
  <a href="https://github.com/201206030/novel-plus"><img alt="GitHub forks" src="https://img.shields.io/github/forks/201206030/novel-plus?logo=github"></a>
  <a href="https://github.com/201206030/novel-plus/releases"><img alt="Lượt tải GitHub" src="https://img.shields.io/github/downloads/201206030/novel-plus/total.svg"></a>
  <a href="https://hub.docker.com/u/201206030"><img alt="Lượt kéo Docker" src="https://img.shields.io/docker/pulls/201206030/novel-front"></a>
</p>

Novel Plus là hệ thống quản lý và đọc truyện đa nền tảng, hỗ trợ giao diện máy tính và thiết bị di động. Dự án gồm cổng đọc, khu vực tác giả, trang quản trị nền tảng và trình quản lý thu thập dữ liệu.

Các chức năng chính gồm đề xuất và tìm kiếm tiếng Việt có dấu/không dấu, chịu lỗi chính tả nhẹ, bảng xếp hạng, đọc chương, bình luận, tủ sách, lịch sử đọc, theo dõi tác giả/truyện và thông báo chương mới, quản lý tác giả, nạp Xu, mua chương, tin tức, báo cáo thống kê, nhiều giao diện, nhiều nguồn thu thập và hỗ trợ sáng tác bằng AI.

## Liên kết

- Mã nguồn: [GitHub](https://github.com/201206030/novel-plus)
- Bản phát hành: [GitHub Releases](https://github.com/201206030/novel-plus/releases)
- Runbook triển khai: [doc/deployment.md](doc/deployment.md)
- Hướng dẫn tích hợp VNPAY: [doc/vnpay.md](doc/vnpay.md)
- Kiến trúc ví và sổ cái: [doc/wallet-ledger.md](doc/wallet-ledger.md)
- KYC và tài chính tác giả: [doc/author-finance.md](doc/author-finance.md)
- Biên bản nghiệm thu P0: [doc/p0-acceptance.md](doc/p0-acceptance.md)
- Trình soạn thảo, bản nháp và lịch xuất bản: [doc/author-editor.md](doc/author-editor.md)
- Giá chương, tự mở khóa và sự kiện miễn phí: [doc/chapter-commercial-policy.md](doc/chapter-commercial-policy.md)
- Nhập và xuất TXT, DOCX, EPUB: [doc/book-import-export.md](doc/book-import-export.md)
- Analytics lượt đọc, giữ chân và doanh thu: [doc/author-analytics.md](doc/author-analytics.md)
- Tìm kiếm tiếng Việt và sửa lỗi chính tả: [doc/vietnamese-search.md](doc/vietnamese-search.md)
- Theo dõi tác giả và thông báo chương mới: [doc/chapter-notifications.md](doc/chapter-notifications.md)
- PWA, đọc offline, tiết kiệm dữ liệu và TTS tiếng Việt: [doc/reader-pwa.md](doc/reader-pwa.md)
- Đề xuất theo hành vi, thể loại và lịch sử đọc: [doc/recommendation.md](doc/recommendation.md)
- Hướng dẫn migration SQL: [doc/sql/readme.md](doc/sql/readme.md)
- Tài liệu gốc: [docs.xxyopen.com](https://docs.xxyopen.com/course/novelplus/1.html)
- Trang giới thiệu: [novel.xxyopen.com](https://novel.xxyopen.com)

## Cấu trúc dự án

```text
novel-plus
├── novel-common   # Mô hình dữ liệu, cấu hình và tiện ích dùng chung
├── novel-front    # Cổng đọc, giao diện di động và khu vực tác giả
├── novel-crawl    # Quản lý nguồn và tác vụ thu thập dữ liệu
├── novel-admin    # Trang quản trị nền tảng
├── templates      # Nguồn của các theme green, orange, dark và blue
├── config         # Cấu hình dùng khi triển khai
└── doc/sql        # SQL khởi tạo và migration tăng dần
```

## Công nghệ

| Công nghệ | Mục đích |
|---|---|
| Java 21 | Nền tảng chạy |
| Spring Boot 3 | `novel-front`, `novel-crawl` và các mô-đun dùng chung |
| Spring Boot 2.7 | `novel-admin` |
| Thymeleaf | Kết xuất giao diện máy chủ |
| MyBatis / MyBatis Dynamic SQL | Truy cập dữ liệu |
| ShardingSphere-JDBC | Phân mảnh cơ sở dữ liệu |
| Redis | Bộ nhớ đệm và trạng thái phiên |
| MySQL | Cơ sở dữ liệu |
| Spring AI | Viết, biên tập nội dung và sinh bìa |
| Apache Shiro / Spring Security | Xác thực và phân quyền |
| Docker | Đóng gói triển khai |

## Yêu cầu môi trường

- JDK 21
- Maven 3.9 trở lên
- MySQL 8
- Redis
- Node.js để chạy kiểm tra cú pháp JavaScript first-party

Các dịch vụ OSS, VNPAY, AI và nguồn thu thập là tùy chọn; chỉ bật khi đã cấu hình thông tin tích hợp tương ứng.

## Khởi tạo cơ sở dữ liệu

1. Tạo một cơ sở dữ liệu MySQL mới với bộ ký tự `utf8mb4`.
2. Chạy [doc/sql/novel_plus.sql](doc/sql/novel_plus.sql) để tạo cấu trúc và dữ liệu mặc định.
3. Chạy lần lượt các migration có ngày mới hơn phiên bản SQL khởi tạo.
4. Với bản Việt hóa, chạy [doc/sql/20260712_vi_localization.sql](doc/sql/20260712_vi_localization.sql).

Migration Việt hóa chỉ cập nhật giá trị tiếng Trung mặc định khi khớp chính xác. Các giá trị đã được quản trị viên tùy chỉnh được giữ nguyên và migration có thể chạy lại an toàn.

## Cấu hình

Các tệp cấu hình chính:

- `novel-common/src/main/resources/application-common-*.yml`: cơ sở dữ liệu, Redis và cấu hình dùng chung.
- `novel-front/src/main/resources/application-*.yml`: cổng đọc, thanh toán, OSS và AI.
- `novel-crawl/src/main/resources/application.yml`: trình thu thập.
- `novel-admin/src/main/resources/application-*.yml`: trang quản trị.

Không ghi khóa API, mật khẩu hoặc khóa bí mật thật vào Git. Dùng biến môi trường hoặc tệp cấu hình triển khai nằm ngoài kho mã nguồn.

## Biên dịch và kiểm thử

Biên dịch reactor chính bằng JDK 21:

```bash
mvn clean test
```

`novel-admin` dùng Spring Boot 2.7 và có thể kiểm tra riêng:

```bash
mvn -f novel-admin/pom.xml clean test
```

Các kiểm tra i18n xác minh:

- catalog `vi_VN` và `zh_CN` có cùng key;
- không có key trùng;
- Java, template và JavaScript first-party không chứa chuỗi Trung ngoài allowlist có giải thích;
- locale mặc định cố định là `vi-VN`.

## Deploy bằng Docker Compose

Bộ Compose khởi động MySQL 8.4, Redis 7, migration Việt hóa, cổng đọc, crawler và trang quản trị. Docker image ứng dụng được build trực tiếp từ source bằng JDK 21; không cần build JAR trước trên máy host.

1. Tạo tệp cấu hình riêng và thay toàn bộ giá trị `change-me`:

   ```bash
   cp .env.example .env
   ```

   Trên PowerShell:

   ```powershell
   Copy-Item .env.example .env
   ```

2. Build và khởi động toàn bộ stack:

   ```bash
   docker compose up -d --build
   ```

3. Kiểm tra trạng thái và log:

   ```bash
   docker compose ps --all
   docker compose logs -f front crawl admin
   ```

Các địa chỉ mặc định:

- front: `http://localhost:8083`
- crawler: `http://localhost:8081`
- admin: `http://localhost:8080`

Tài khoản crawler lấy từ `CRAWLER_ADMIN_USERNAME` và `CRAWLER_ADMIN_PASSWORD` trong `.env`. Database mới có tài khoản quản trị seed `admin/admin`; phải đổi mật khẩu ngay sau lần đăng nhập đầu tiên.

MySQL, Redis, ảnh tải lên và nội dung truyện dùng named volume nên được giữ lại khi chạy `docker compose down`. Lệnh `docker compose down -v` xóa toàn bộ volume và dữ liệu, chỉ dùng khi chủ động khởi tạo lại môi trường.

Các migration từ `20260712_vi_localization.sql` đến `20260728_chapter_commercial_policy.sql` chạy như một service one-shot ở mỗi lần khởi động và có thể chạy lặp lại. Chúng bổ sung dữ liệu Việt hóa, hardening VNPAY, sổ cái kép, KYC/rút thu nhập, refund/chargeback/VietQR, kiểm duyệt-bản quyền, báo cáo-bảo mật, editor bản nháp, kiểu lưu SimHash nhất quán, queue kiểm duyệt bìa, analytics tác giả, tìm kiếm tiếng Việt, thông báo chương, recommendation, cộng tác, reader-state, provenance AI và chính sách thương mại chương. Nếu database cũ đã có `out_trade_no` trùng, migration chủ động dừng để quản trị viên đối soát thay vì tự xóa hoặc gộp lịch sử. Khi nâng cấp từ phiên bản cũ hơn, vẫn phải chạy các migration trung gian theo [hướng dẫn SQL](doc/sql/readme.md).

Các khóa AI, VNPAY, OSS và email là tùy chọn, được đọc từ `.env`; không ghi khóa thật vào source hoặc image. IPN VNPAY phải được cấu hình tại cổng merchant thành `https://<ten-mien>/pay/vnpay/ipn`; URL này cần HTTPS công khai. Trong production nên đặt reverse proxy TLS phía trước ba cổng HTTP, dùng Docker secrets hoặc secret manager và sao lưu volume MySQL định kỳ.

VietQR mặc định tắt và yêu cầu tài khoản nhận tiền thật cùng webhook secret tối thiểu 32 ký tự. NAPAS/payout ngân hàng không giả lập thành công khi chưa có hợp đồng adapter thật. Phát hành chứng từ cũng mặc định tắt cho tới khi cấu hình pháp nhân/MST và hoàn tất phê duyệt thuế; các báo cáo kỹ thuật không thay thế hóa đơn điện tử hợp pháp.

Để bật VNPAY, cấu hình các biến sau trong `.env`:

```dotenv
VNPAY_ENABLED=true
VNPAY_TMN_CODE=ma_website_do_vnpay_cap
VNPAY_HASH_SECRET=khoa_bi_mat_do_vnpay_cap
VNPAY_PAY_URL=https://sandbox.vnpayment.vn/paymentv2/vpcpay.html
VNPAY_RETURN_URL=https://ten-mien-cua-ban/pay/vnpay/return
VNPAY_QUERY_URL=https://sandbox.vnpayment.vn/merchant_webapi/api/transaction
VNPAY_XU_PER_1000_VND=100
VNPAY_ALLOWED_AMOUNTS_VND=10000,30000,50000,100000,200000,500000
VNPAY_RECONCILIATION_ENABLED=true
VNPAY_SERVER_IP=dia_chi_ip_cong_khai_cua_may_chu
VNPAY_RECONCILIATION_DELAY_MS=300000
VNPAY_RECONCILIATION_INITIAL_DELAY_MS=60000
VNPAY_RECONCILIATION_MIN_AGE_MINUTES=20
VNPAY_RECONCILIATION_MAX_AGE_DAYS=30
VNPAY_RECONCILIATION_BATCH_SIZE=50
```

Khi chuyển sang production, thay cả `VNPAY_PAY_URL`, `VNPAY_QUERY_URL`, mã website, khóa bí mật và IP máy chủ bằng thông tin VNPAY production. Không dùng Return URL để cộng Xu; hệ thống chỉ ghi nhận tiền từ IPN hoặc QueryDr có chữ ký hợp lệ, đúng merchant, đúng mã đơn, đúng kênh và đúng số tiền. Số Xu được chốt ngay lúc tạo đơn nên thay đổi tỷ lệ sau đó không làm sai đơn đang chờ. QueryDr tự đối soát các đơn quá 20 phút khi IPN bị gián đoạn; nhiều replica giành quyền xử lý bằng optimistic update và cập nhật số dư vẫn có tính idempotent. Mã kênh VNPAY trong `order_pay.pay_channel` là `4`; các đơn từ cổng thanh toán cũ vẫn được giữ để đối soát lịch sử nhưng không còn endpoint hoặc giao diện tạo giao dịch mới qua cổng đó.

Xem [hướng dẫn VNPAY](doc/vnpay.md) để cấu hình merchant, khai báo IPN, hiểu trạng thái đơn, kiểm tra QueryDr và xử lý sự cố. Xem [runbook triển khai](doc/deployment.md) cho quy trình nâng cấp, sao lưu, healthcheck và rollback.

Dừng stack:

```bash
docker compose down
```

## Chạy ứng dụng

Sau khi cấu hình cơ sở dữ liệu và Redis, có thể chạy từng ứng dụng bằng Maven:

```bash
mvn -pl novel-front -am spring-boot:run
mvn -pl novel-crawl -am spring-boot:run
mvn -f novel-admin/pom.xml spring-boot:run
```

Kiểm tra lại cổng trong tệp YAML của từng ứng dụng trước khi truy cập.

## Theme

Thư mục `templates/<theme>` là nguồn theme. `novel-front` cung cấp lớp template nền khi đóng gói, còn theme được chọn là lớp ghi đè. Các theme hỗ trợ:

- `green`
- `orange`
- `dark`
- `blue`

Đóng gói một theme bằng lifecycle Maven đầy đủ:

```powershell
mvn -pl novel-front -am -Dtheme.name=green package
```

Có thể thay `green` bằng `orange`, `dark` hoặc `blue`. Pha `generate-resources` chủ động xóa riêng `target/classes/templates` và `target/classes/static` trước khi chép runtime base rồi ghi đè theme, vì vậy đổi theme liên tiếp không để lại file từ lần đóng gói trước. Không gọi trực tiếp `resources:resources` để đổi theme vì goal rời này bỏ qua pha chuẩn bị nói trên.

Tiếng Việt là ngôn ngữ hiển thị mặc định. Catalog tiếng Trung chỉ được giữ làm fallback nội bộ; giao diện chưa cung cấp bộ chọn ngôn ngữ.

## AI

Novel Plus hỗ trợ mở rộng, rút gọn, viết tiếp, trau chuốt nội dung và sinh ảnh bìa. Prompt first-party yêu cầu đầu ra tiếng Việt tự nhiên.

Ví dụ cấu hình dùng endpoint tương thích OpenAI:

```yaml
spring:
  ai:
    openai:
      base-url: ${OPENAI_BASE_URL:https://api.openai.com}
      api-key: ${OPENAI_API_KEY:disabled}
      chat:
        options:
          model: ${OPENAI_CHAT_MODEL:gpt-4.1-mini}
      image:
        enabled: ${AI_IMAGE_ENABLED:false}
        options:
          model: ${OPENAI_IMAGE_MODEL:gpt-image-1}
```

Chất lượng và chi phí phụ thuộc nhà cung cấp và mô hình được cấu hình.

## Nguyên tắc dữ liệu

- Đơn vị tiền nội bộ hiển thị là **Xu**.
- Không tự động dịch tên tác phẩm, tên tác giả, nội dung chương, bình luận người dùng hoặc dữ liệu do crawler thu thập.
- Endpoint, JSON field, permission code, enum lưu DB và tên bảng/cột được giữ nguyên.
- Dữ liệu tích hợp bắt buộc bằng tiếng Trung phải nằm trong allowlist và có lý do.

## Tuyên bố miễn trừ trách nhiệm

Chức năng thu thập dữ liệu chỉ nhằm phục vụ kiểm thử và nhập dữ liệu hợp pháp. Người vận hành phải tuân thủ bản quyền, điều khoản của nguồn dữ liệu và pháp luật áp dụng. Tác giả dự án không chịu trách nhiệm cho việc sử dụng hệ thống vào mục đích vi phạm pháp luật.

## Giấy phép và đóng góp

Hãy xem thông tin giấy phép trong kho mã nguồn. Khi đóng góp, vui lòng chạy test, kiểm tra cú pháp JavaScript và `git diff --check` trước khi gửi thay đổi.
