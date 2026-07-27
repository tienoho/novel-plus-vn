# Tìm kiếm tiếng Việt

## Phạm vi

Luồng `/book/searchByPage` hỗ trợ:

- tìm tên truyện và tên tác giả có dấu hoặc không dấu;
- ưu tiên khớp chính xác, khớp đầu chuỗi và khớp chuỗi con;
- chịu lỗi gõ nhẹ, bao gồm đảo hai ký tự liền nhau như `ngueyn` → `nguyen`;
- giữ nguyên bộ lọc thể loại, trạng thái, VIP, độ dài, thời gian cập nhật và cách sắp xếp;
- giữ nguyên cấu trúc response `PageBean` hiện có.

Hệ thống không sửa hoặc dịch tên truyện/tác giả trong database. Việc chuẩn hóa chỉ diễn ra trên cột tìm kiếm sinh tự động và trong bộ nhớ khi xếp hạng ứng viên.

## Cách hoạt động

1. MySQL tìm khớp trực tiếp trên `book_name_search` và `author_name_search` với collation `utf8mb4_0900_ai_ci`.
2. Nếu không có khớp trực tiếp, FULLTEXT ngram lấy tối đa 500 ứng viên có quan hệ ký tự gần nhất.
3. Ứng dụng bỏ dấu, chuyển `đ` thành `d`, chuẩn hóa khoảng trắng và dùng Damerau–Levenshtein theo cửa sổ từ.
4. Chỉ ứng viên nằm trong ngưỡng lỗi theo độ dài truy vấn mới được trả về; khoảng cách nhỏ hơn và tên có độ dài gần hơn được ưu tiên.

Ngram chỉ dùng để thu hẹp ứng viên, không phải kết luận cuối. Cách tách hai pha này tránh việc tên dài hoặc chuỗi chỉ trùng một vài bigram được xếp trên kết quả đúng.

## Migration

Chạy `doc/sql/20260726_vietnamese_search.sql` sau các migration trước đó. Migration:

- thêm hai cột `STORED GENERATED`, không thay đổi dữ liệu nguồn hoặc unique key hiện hữu;
- tạo FULLTEXT index `ft_book_vi_search` với parser ngram;
- kiểm tra `information_schema` trước mỗi thay đổi nên có thể chạy lại an toàn.

Việc thêm cột stored và FULLTEXT index có thể rebuild bảng `book`. Với database production lớn, phải thử trên bản sao, đo thời gian và chạy trong cửa sổ bảo trì. Runtime yêu cầu MySQL 8 và `ngram_token_size=2` (giá trị mặc định); nếu thay đổi giá trị này phải rebuild FULLTEXT index và chạy lại regression tìm kiếm.

## Kiểm thử

Test cục bộ:

```powershell
mvn -Dtest=VietnameseSearchNormalizationTest,VietnameseSearchMatcherTest,VietnameseSearchPackagingTest `
  -Dsurefire.failIfNoSpecifiedTests=false -pl novel-front -am test
```

Integration MySQL cần database cô lập đã chạy đủ migration:

```powershell
$env:SPRING_DATASOURCE_URL = 'jdbc:mysql://127.0.0.1:3306/novel_plus'
$env:SPRING_DATASOURCE_DRIVER_CLASS_NAME = 'com.mysql.cj.jdbc.Driver'
$env:SPRING_DATASOURCE_USERNAME = 'root'
$env:SPRING_DATASOURCE_PASSWORD = '<mat-khau-db-test>'
mvn -Dp1.search.mysql.it=true -Dtest=VietnameseSearchMySqlIntegrationTest `
  -Dsurefire.failIfNoSpecifiedTests=false -pl novel-front -am test
```

Integration kiểm tra tìm kiếm có dấu/không dấu, typo tên truyện/tác giả, lọc danh mục và metadata phân trang.
