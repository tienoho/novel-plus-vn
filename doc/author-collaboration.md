# Đồng tác giả và biên tập viên theo tác phẩm

Ngày nghiệm thu kỹ thuật: **27/07/2026**.

## Mô hình quyền

Chủ sở hữu là `book.author_id` và có toàn bộ quyền ngầm định. Hai vai trò cộng tác là:

- `CO_AUTHOR`: mặc định có cả sáu quyền.
- `EDITOR`: mặc định có `MANAGE_CHAPTERS` và `MANAGE_STORY`; không có quyền xuất bản, sửa metadata hoặc xem analytics.

Chủ sở hữu có thể ghi đè từng quyền khi thêm hoặc cập nhật cộng tác viên:

- `EDIT_BOOK`: sửa thông tin và bìa.
- `PUBLISH_BOOK`: thay đổi trạng thái xuất bản truyện.
- `MANAGE_CHAPTERS`: đọc nội dung, lịch sử và tự lưu bản nháp.
- `PUBLISH_CHAPTERS`: tạo, sửa, xóa hoặc lên lịch chương live.
- `MANAGE_STORY`: dùng chung kho tư liệu trong namespace của chủ sở hữu.
- `VIEW_ANALYTICS`: xem analytics tác phẩm; truy vấn doanh thu luôn dùng ví của chủ sở hữu.

Cộng tác viên không nhận quyền tài chính, KYC, thu nhập, payout, bằng chứng sở hữu, kháng nghị bản quyền hoặc quyền quản trị cộng tác viên.

## API

Tất cả endpoint yêu cầu đăng nhập bằng tài khoản tác giả hoạt động:

```text
GET    /author/books/{bookId}/access
GET    /author/books/{bookId}/collaborators
POST   /author/books/{bookId}/collaborators
PUT    /author/books/{bookId}/collaborators/{id}
DELETE /author/books/{bookId}/collaborators/{id}?expectedVersion={version}
```

Chỉ chủ sở hữu được gọi bốn endpoint quản trị. `PUT` và `DELETE` bắt buộc gửi phiên bản hiện tại; phiên bản cũ trả mã `4012` và không ghi đè dữ liệu mới. Thêm, sửa và gỡ quyền đều ghi snapshot vào `author_book_collaborator_audit`; trigger database cấm update/delete audit.

Trang quản trị là `/author/collaborators.html?bookId={bookId}`. Runtime cung cấp trang fallback cho cả bốn theme; `green` và `orange` chỉ overlay trang danh sách truyện, còn `dark` và `blue` dùng toàn bộ runtime fallback.

## Migration và kiểm thử

Migration `doc/sql/20260727_author_collaboration.sql` có thể chạy lại và đã được đưa vào service `migrate` của Compose. Luôn sao lưu database và thử trên bản sao production trước khi nâng cấp.

Chạy unit test:

```powershell
mvn -Dtest=AuthorBookCollaborationServiceImplTest,AuthorChapterDraftServiceImplTest,AuthorStoryServiceImplTest,AuthorAnalyticsServiceImplTest,BookServiceEditorConsistencyTest `
  -Dsurefire.failIfNoSpecifiedTests=false -pl novel-front -am test
```

Integration MySQL phải chạy trên database cô lập đã áp dụng migration:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://localhost:3306/novel_plus'
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'com.mysql.cj.jdbc.Driver'
$env:SPRING_DATASOURCE_USERNAME = 'novel'
$env:SPRING_DATASOURCE_PASSWORD = '<mat-khau-test>'
mvn -Dcollaboration.mysql.it=true -Dtest=AuthorCollaborationMySqlIntegrationTest `
  -Dsurefire.failIfNoSpecifiedTests=false -pl novel-front -am test
```

Integration dùng transaction rollback và kiểm tra owner implicit, role defaults, optimistic version, revoke fail-closed, danh sách truyện accessible và audit bất biến.
