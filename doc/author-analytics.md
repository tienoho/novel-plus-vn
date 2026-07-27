# Analytics dành cho tác giả

Analytics P1 cung cấp số liệu theo truyện và từng chương mà không lưu tài khoản, địa chỉ IP hoặc mã trình duyệt thô của độc giả. Trình duyệt tạo một mã ngẫu nhiên bền trong `localStorage`; backend chỉ lưu SHA-256 của mã này.

## Số liệu

- **Độc giả**: số mã độc giả ẩn danh duy nhất trong khoảng ngày.
- **Lượt đọc chương**: số cặp độc giả–chương có ít nhất một event.
- **Tỷ lệ đọc hết**: số cặp độc giả–chương có event `COMPLETE` chia cho tổng cặp độc giả–chương.
- **Giữ chân sang chương sau**: trong các độc giả đã đọc một chương có chương kế tiếp, tỷ lệ tiếp tục mở chương kế tiếp.
- **Tiến độ và thời gian trung bình**: giá trị lớn nhất trong một phiên đọc của từng độc giả–chương, sau đó lấy trung bình.
- **Doanh thu gộp**: tổng `user_buy_record.buy_amount` trong khoảng ngày.
- **Xu về tác giả**: tổng bút toán dương thực tế vào ví `AUTHOR_REVENUE_XU`; không nhân lại tỷ lệ chia doanh thu hiện tại.

Dashboard chỉ cho tác giả sở hữu truyện truy cập. Khoảng truy vấn tối đa 366 ngày và bảng chương được phân trang tối đa 100 dòng mỗi lần.

## Ghi sự kiện

Trang đọc chỉ bật analytics khi nội dung chương đã được mở khóa. Script gửi:

- `START` khi bắt đầu đọc;
- `PROGRESS` tại các mốc 25%, 50%, 75% và khi rời trang;
- `COMPLETE` sau khi đạt ít nhất 90% trang và ở trang tối thiểu 5 giây.

`client_event_id` là khóa unique nên retry hoặc `sendBeacon` lặp không nhân đôi sự kiện. Endpoint bị rate limit theo IP. Bảng `reader_chapter_event` và hai trigger trong [migration analytics](sql/20260726_author_analytics.sql) chặn sửa/xóa lịch sử.

## API

```text
POST /book/analytics/read-event
GET  /author/analytics/summary?bookId=<id>&startDate=yyyy-MM-dd&endDate=yyyy-MM-dd
GET  /author/analytics/chapters?bookId=<id>&startDate=yyyy-MM-dd&endDate=yyyy-MM-dd&page=1&limit=20
```

URL dashboard:

```text
/author/author_analytics.html?bookId=<id>
```

## Vận hành

- Chạy migration trước khi deploy image mới; ứng dụng cũ không ghi vào bảng analytics nên có thể rolling deploy.
- Theo dõi tốc độ tăng của `reader_chapter_event` và thời gian các truy vấn CTE.
- Chốt thời hạn lưu dữ liệu với chính sách dữ liệu cá nhân trước production. Khi cần xóa dữ liệu hết hạn, không được tắt trigger và xóa trực tiếp tùy tiện; phải phát hành migration retention có phê duyệt và audit riêng.
- Số liệu là analytics phía client nên có thể bị chặn bởi trình duyệt hoặc phần mềm riêng tư; không dùng làm căn cứ thanh toán. Nguồn doanh thu chuẩn vẫn là sổ cái.

## Kiểm thử

Integration MySQL có điều kiện:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3306/novel_plus'
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'com.mysql.cj.jdbc.Driver'
$env:SPRING_DATASOURCE_USERNAME = 'root'
$env:SPRING_DATASOURCE_PASSWORD = '<mat-khau-db-test>'
mvn -Dp1.analytics.mysql.it=true -Dtest=AuthorAnalyticsMySqlIntegrationTest `
  -Dsurefire.failIfNoSpecifiedTests=false -pl novel-front -am test
```

Test chứng minh idempotency, event bất biến, completion, retention, ownership và doanh thu chương từ sổ cái trên MySQL 8.4 thật.
