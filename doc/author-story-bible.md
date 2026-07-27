# Kho tư liệu tác phẩm

Kho tư liệu giúp tác giả quản lý riêng tư bốn nhóm dữ liệu theo từng tác phẩm: dàn ý, nhân vật, địa điểm và dòng thời gian. Dữ liệu này không được hiển thị cho độc giả, crawler hoặc API công khai.

## Mô hình và quyền sở hữu

`author_story_item` lưu `author_id`, `book_id`, loại, tiêu đề, nội dung, mốc thời gian tự do, thứ tự và optimistic `version`. Mốc thời gian là văn bản vì thế giới truyện có thể dùng niên đại hư cấu, không bị ép thành ngày lịch.

Mỗi request lấy tác giả từ JWT/cookie hiện tại. Service kiểm tra tác phẩm thuộc đúng tác giả trước khi liệt kê hoặc tạo; update/delete luôn lọc theo cả `id` và `author_id`. API không nhận `authorId` từ client.

## API

```http
GET    /author/story-items?bookId=...&type=OUTLINE
POST   /author/story-items
PUT    /author/story-items/{itemId}
DELETE /author/story-items/{itemId}?expectedVersion=...
```

Các loại hợp lệ là `OUTLINE`, `CHARACTER`, `LOCATION` và `TIMELINE`. Update/delete yêu cầu version hiện tại; version cũ bị từ chối để một tab không ghi đè thay đổi từ tab khác.

Trang giao diện nằm tại `/author/story_bible.html?bookId=...` và phải được mở từ danh sách tác phẩm. Runtime cung cấp template nền cho cả bốn theme; green/orange chỉ cần giữ liên kết tại trang danh sách tác phẩm, còn dark/blue kế thừa trang tác giả từ runtime.

## Migration và kiểm thử

Chạy `20260727_author_story_bible.sql` sau migration recommendation. Migration chỉ tạo bảng/index mới và có thể chạy lại an toàn.

Kiểm thử tối thiểu phải chứng minh:

- tác giả không thể đọc hoặc tạo tư liệu cho truyện của người khác;
- loại ngoài allowlist bị từ chối;
- retry update/delete bằng version cũ không ghi đè dữ liệu;
- nội dung được render bằng text node, không chèn HTML thực thi;
- template và link vẫn tồn tại sau khi merge runtime với từng theme.

## Nghiệm thu runtime

Ngày nghiệm thu kỹ thuật: **27/07/2026**.

- Image `novel-plus/front:local` được build lại bằng target `front`; container front chạy healthy với template runtime được phủ vào cả bốn theme.
- Smoke API trên MySQL thật tạo đủ bốn loại, liệt kê đúng bốn item, update tăng version từ `0` lên `1`, request dùng version cũ trả `4007`, loại ngoài allowlist trả `4008` và truyện không thuộc tác giả trả `5004` cho cả đọc lẫn tạo.
- UI thực hiện thành công tạo, sửa và xóa một item. Nội dung chứa thẻ `script`/`img` được hiển thị như văn bản, không tạo node thực thi và không đặt biến XSS thử nghiệm.
- Hộp xác nhận dùng tiêu đề `Thông báo`, nút `Đồng ý`/`Hủy`; `header.js` được nạp trước `common.js` nên không còn lỗi `$.cookie is not a function`.
- Desktop dùng grid hai cột; tại viewport 390 px và 320 px, grid chuyển thành một cột, không còn phần tử hoặc nội dung vượt viewport và console không có error/warn.
- Sau smoke, hai book fixture `9970002001`/`9970002002` cùng toàn bộ `author_story_item` liên quan đã được xóa; user seed `1255060328322027520` và author seed `2` vẫn tồn tại.
