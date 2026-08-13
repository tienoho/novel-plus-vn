# Nhập và xuất TXT, DOCX, EPUB

Khu vực tác giả cho phép nhập bản thảo vào một tác phẩm đã có và xuất các chương đã phát hành. Tính năng dùng quyền theo tác phẩm, không tự xuất bản nội dung nhập và không lấy giá, quyền truy cập hoặc metadata thương mại từ tệp của người dùng.

## Quyền và hành vi

- Chủ sở hữu hoặc cộng tác viên có quyền `MANAGE_CHAPTERS` mới được nhập/xuất.
- Import tạo từng chương thành bản nháp `DRAFT`, `isVip=0`, không gắn chương nguồn và không đặt lịch xuất bản.
- Toàn bộ một lần import nằm trong một transaction. Nếu bất kỳ chương nào không lưu được, mọi draft/audit vừa tạo trong lô đều rollback.
- Client key được dẫn xuất từ tác phẩm và SHA-256 của tệp. Gửi lại cùng tệp cho cùng tác phẩm là idempotent; cùng tệp ở tác phẩm khác có namespace riêng.
- Export chỉ lấy chương có `audit_status=1`, sắp xếp theo `index_num` rồi `id`; draft, chương chờ duyệt và chương bị gỡ không được đưa vào tệp.
- Nội dung HTML lưu trữ được chuyển về plain text sau khi loại script/style và thẻ markup. Entity đã escape được giải mã sau bước loại thẻ để không biến văn bản của tác giả thành HTML thực thi.

## Định dạng

### TXT

- Đọc UTF-8; hỗ trợ UTF-8 BOM và UTF-16 LE/BE có BOM.
- Tiêu đề bắt đầu bằng `Chương`, `Chuong` hoặc `Chapter` mở một chương mới.
- Xuất UTF-8, xuống dòng CRLF để tương thích trình soạn thảo Windows.

### DOCX

- Đọc `word/document.xml`; nhận paragraph style `Heading*` hoặc tiêu đề chương theo nội dung.
- Xuất OOXML tối thiểu có content type, relationship, `styles.xml`, trang A4, lề 1 inch, font Times New Roman, ngôn ngữ `vi-VN`, Title và Heading 1.
- XML chặn DOCTYPE, external entity, external DTD/schema và XInclude.

### EPUB 3

- Đọc package document từ `META-INF/container.xml` và chương theo thứ tự `spine`.
- Xuất EPUB 3 có navigation document, OPF, metadata tiếng Việt và XHTML cho từng chương.
- Entry `mimetype` luôn đứng đầu, không nén và có nội dung chính xác `application/epub+zip`.

## Giới hạn an toàn

| Giới hạn | Giá trị |
|---|---:|
| Tệp upload | 20 MB |
| Tổng text xử lý/xuất | 20 MB |
| Tổng giải nén archive | 100 MB |
| Một entry archive | 20 MB |
| Số entry archive | 2.048 |
| Số chương | 500 |
| Một chương | 2.000.000 ký tự |

Archive bị từ chối nếu có đường dẫn tuyệt đối, ký tự `\\`, drive letter, `..`, path sau normalize khác path gốc hoặc entry trùng. Parser không giải nén ra filesystem.

## HTTP API

### Import

```http
POST /author/books/{bookId}/import
Content-Type: multipart/form-data

file=<TXT|DOCX|EPUB>
```

Response trả số draft, ID draft và tên chương. Định dạng được suy ra từ phần mở rộng tên tệp, không tin `Content-Type` do trình duyệt gửi.

### Export

```http
GET /author/books/{bookId}/export?format=TXT|DOCX|EPUB
```

Response dùng MIME đúng định dạng, `Content-Disposition` UTF-8, `Cache-Control: no-store` và `X-Content-Type-Options: nosniff`.

## Kiểm thử

```powershell
mvn -Dtest=BookTransferCodecTest,AuthorBookTransferServiceImplTest,AuthorBookTransferControllerTest,AuthorBookTransferPackagingTest `
  -Dsurefire.failIfNoSpecifiedTests=false -pl novel-front -am test
```

Integration rollback phải chạy trên MySQL cô lập đã áp dụng toàn bộ migration:

```powershell
mvn -Dp1.transfer.mysql.it=true -Dtest=AuthorBookTransferMySqlIntegrationTest `
  -Dsurefire.failIfNoSpecifiedTests=false -pl novel-front -am test
```

Test bao phủ round-trip ba định dạng, UTF-16 BOM, XXE, path traversal, duplicate entry, archive expansion, ngân sách tổng, thứ tự EPUB spine, styles DOCX, quyền, header tải xuống và rollback toàn lô trên MySQL thật.
