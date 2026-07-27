# Theo dõi tác giả và thông báo chương mới

## Phạm vi

Độc giả có thể theo dõi truyện bằng tủ sách, theo dõi riêng tác giả và nhận thông báo trong ứng dụng khi một chương lần đầu đạt `audit_status = 1`. Hệ thống không gửi lại thông báo cho chương cũ khi cài migration và không gửi email/push ở giai đoạn này.

Không đổi endpoint, field JSON hoặc mã trạng thái lưu trong database. Tên truyện và tên chương được giữ nguyên như dữ liệu nguồn.

## Luồng xử lý

1. Trigger `trg_book_index_publish_event_insert` ghi outbox khi chương được tạo ở trạng thái đã duyệt.
2. Trigger `trg_book_index_publish_event_approve` ghi outbox khi chương chuyển từ trạng thái khác sang đã duyệt.
3. Unique key trên `chapter_publish_event.chapter_id` bảo đảm một chương chỉ có một event, kể cả khi bị trả về kiểm duyệt rồi duyệt lại.
4. Scheduler trong `front` claim event, fan-out tới người có truyện trong `user_bookshelf` hoặc có tác giả trong `user_author_follow`, rồi đánh dấu `PROCESSED` trong cùng transaction.
5. Unique key `(user_id, notification_type, chapter_id)` bảo đảm người theo dõi cả truyện lẫn tác giả chỉ nhận một thông báo.

Trigger chỉ ghi outbox, không fan-out trực tiếp, nên thao tác xuất bản không phải chờ số lượng người theo dõi. Nếu `front` dừng, event vẫn ở `PENDING` và được xử lý sau khi ứng dụng hoạt động lại.

## Bảng và migration

Migration [20260726_chapter_notifications.sql](sql/20260726_chapter_notifications.sql) tạo:

- `user_author_follow`: quan hệ độc giả–tác giả;
- `chapter_publish_event`: transactional outbox của chương đã duyệt;
- `user_notification`: hộp thông báo theo độc giả;
- hai trigger bắt lần insert và lần chuyển sang trạng thái đã duyệt.

Migration có thể chạy lặp lại. Migration cố ý không backfill `chapter_publish_event` từ các chương đã có trước đó để tránh tạo một đợt thông báo lớn sau nâng cấp.

## API

Các API giữ cơ chế xác thực người dùng hiện tại:

| Method | Endpoint | Mục đích |
|---|---|---|
| `GET` | `/user/notifications?curr=1&limit=20` | Danh sách thông báo phân trang |
| `GET` | `/user/notifications/unread-count` | Số thông báo chưa đọc |
| `POST` | `/user/notifications/{id}/read` | Đánh dấu một thông báo đã đọc |
| `POST` | `/user/notifications/read-all` | Đánh dấu tất cả đã đọc |
| `GET` | `/user/follows/authors/{authorId}` | Kiểm tra trạng thái theo dõi |
| `POST` | `/user/follows/authors/{authorId}` | Theo dõi tác giả |
| `DELETE` | `/user/follows/authors/{authorId}` | Bỏ theo dõi tác giả |

Giao diện hộp thư nằm tại `/user/notifications.html`. Khách chưa đăng nhập được chuyển tới trang đăng nhập; API trả mã chưa đăng nhập theo hợp đồng hiện có.

## Retry và tính idempotent

Khi fan-out lỗi, transaction claim bị rollback để event trở lại `PENDING`. Worker ghi `attempts`, `last_error` và `next_attempt_time` trong transaction riêng. Backoff lần lượt là 1, 2, 4, 8, 16, 32 và tối đa 60 phút. Lần lỗi thứ 10 chuyển event sang `FAILED`; worker không tự thử lại event này.

Theo dõi backlog:

```sql
SELECT status, COUNT(*) AS event_count, MIN(create_time) AS oldest_event
FROM chapter_publish_event
GROUP BY status;

SELECT id, chapter_id, book_id, attempts, next_attempt_time, last_error
FROM chapter_publish_event
WHERE status IN ('PENDING', 'FAILED')
ORDER BY id;
```

Sau khi đã xử lý nguyên nhân, chỉ requeue đúng event cần thiết:

```sql
UPDATE chapter_publish_event
SET status = 'PENDING', attempts = 0,
    next_attempt_time = CURRENT_TIMESTAMP, last_error = NULL
WHERE id = :event_id AND status = 'FAILED';
```

Không xóa notification để “chạy lại”: unique key là lớp bảo vệ chống gửi trùng. Hiện chưa có job retention tự động; phải chốt thời gian lưu notification/event theo nhu cầu vận hành và pháp lý trước khi bổ sung tác vụ dọn dữ liệu.

## Cấu hình

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---:|---|
| `NOTIFICATION_CHAPTER_SCHEDULE_DELAY_MS` | `15000` | Khoảng nghỉ giữa hai lượt quét sau khi lượt trước kết thúc |
| `NOTIFICATION_CHAPTER_SCHEDULE_INITIAL_DELAY_MS` | `15000` | Thời gian chờ sau khi `front` khởi động |

Mỗi lượt lấy tối đa 20 event. Chỉ giảm delay sau khi đo backlog và tải ghi trên MySQL.

## Kiểm thử

Chạy integration trên MySQL 8.4 đã áp dụng đủ migration:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3307/novel_plus?useUnicode=true&characterEncoding=utf-8&serverTimezone=Asia/Ho_Chi_Minh'
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'com.mysql.cj.jdbc.Driver'
$env:SPRING_DATASOURCE_USERNAME = 'root'
$env:SPRING_DATASOURCE_PASSWORD = ''
mvn '-Dp1.notifications.mysql.it=true' `
  '-Dtest=ChapterNotificationMySqlIntegrationTest' `
  '-Dsurefire.failIfNoSpecifiedTests=false' -pl novel-front -am test
```

Test phải chứng minh insert/approve tạo event đúng một lần, fan-out không trùng, đọc/chưa đọc, follow/unfollow, retry/backoff và trạng thái `FAILED` sau lần lỗi thứ 10.
