# Trình soạn thảo chương cho tác giả

Trình soạn thảo lưu bản nháp riêng tư trong `author_chapter_draft`. Nội dung chỉ xuất hiện trong `book_index` và `book_content*` sau khi transaction xuất bản hoàn tất. Mỗi request đều lấy tác giả từ phiên đăng nhập; client không được truyền `authorId`.

## Luồng trạng thái

```text
DRAFT --schedule--> SCHEDULED --worker/publish now--> PUBLISHING --> PUBLISHED
   ^                     |
   +----cancel schedule--+
```

- `DRAFT`: cho phép tự lưu bằng optimistic version.
- `SCHEDULED`: khóa trường chỉnh sửa trên UI; tác giả phải hủy lịch trước khi sửa, nhưng vẫn có thể xuất bản ngay.
- `PUBLISHING`: trạng thái claim nội bộ trong cùng transaction xuất bản.
- `PUBLISHED`: lưu `published_index_id`; retry scheduler không tạo chương thứ hai.
- `CANCELLED`: dành cho luồng hủy bản nháp trong tương lai, hiện không có endpoint xóa lịch sử.

`client_key` là khóa idempotency ổn định của một tab soạn thảo. Retry cùng khóa và cùng payload trả lại bản nháp hiện có; cùng khóa với payload khác bị từ chối để người dùng tải lại version mới nhất.

## Tự lưu và khôi phục

- Debounce sau 2 giây và kiểm tra định kỳ mỗi 15 giây.
- Mỗi update gửi `expectedVersion`; version cũ bị từ chối, không ghi đè nội dung từ tab khác.
- `localStorage` chỉ giữ ID, version, client key và trạng thái; bản thảo đầy đủ nằm ở database.
- Lỗi mạng khi khôi phục không xóa ID draft cục bộ. Sau khi kết nối lại, editor có thể tải đúng bản nháp thay vì tạo bản trùng.
- Draft đã lên lịch là chỉ đọc tới khi tác giả bấm “Hủy lịch”.

## API

```http
POST /author/drafts/autosave
GET  /author/drafts/{draftId}
GET  /author/drafts?status=&page=1&limit=20
POST /author/drafts/{draftId}/schedule
POST /author/drafts/{draftId}/cancel-schedule?expectedVersion=...
POST /author/drafts/{draftId}/publish?expectedVersion=...
GET  /author/chapterHistory/{indexId}
GET  /author/chapterHistory/compare?indexId=...&v1=...&v2=...
```

Lịch xuất bản phải từ 30 giây đến 366 ngày trong tương lai. Scheduler lấy tối đa 100 draft mỗi vòng, claim bằng `status + version` và ghi lỗi gần nhất vào draft nếu publish thất bại.

## Lịch sử phiên bản

Khi tạo chương, hệ thống ghi version 1 vào `book_content_history`. Mỗi lần sửa giữ khóa hàng `book_index`, tạo version kế tiếp và cập nhật tổng số chữ của tác phẩm trong cùng transaction. Với chương legacy chưa có history, lần sửa đầu tiên chụp nội dung cũ thành version 1 trước khi ghi version 2.

`author_chapter_draft_event` là audit trạng thái bất biến; trigger MySQL chặn `UPDATE` và `DELETE`.

## Cấu hình vận hành

```dotenv
AUTHOR_EDITOR_SCHEDULE_BATCH_SIZE=20
AUTHOR_EDITOR_SCHEDULE_DELAY_MS=30000
AUTHOR_EDITOR_SCHEDULE_INITIAL_DELAY_MS=30000
```

Không giảm delay hoặc tăng batch khi chưa đo tải database. Nhiều replica có thể cùng đọc danh sách đến hạn, nhưng chỉ một replica claim được cùng draft nhờ optimistic update.

## Migration

Chạy theo thứ tự:

1. `20260725_moderation_copyright.sql` tạo lịch sử chương và các trường kiểm duyệt.
2. `20260726_author_editor.sql` tạo draft, lịch và audit.
3. `20260726_simhash_storage.sql` đồng bộ SimHash thành chuỗi nhị phân 64 ký tự.

Mọi migration phải được thử trên bản sao dữ liệu và chạy lặp lại trước khi nâng cấp production.
