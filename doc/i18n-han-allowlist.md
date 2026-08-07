# Allowlist chữ Hán cho đợt Việt hóa

Tài liệu này ghi nhận các vị trí được phép giữ chữ Hán sau khi Việt hóa. Bộ kiểm tra `node scripts/verify-i18n.mjs` áp dụng cùng phạm vi và sẽ báo lỗi nếu chữ Hán xuất hiện ngoài danh sách.

## Bundle fallback

- `**/messages_zh_CN.properties`: bản dịch fallback tiếng Trung, không hiển thị khi locale cố định là `vi-VN`.

## Hợp đồng dữ liệu và rule crawler

- `CrawlController.java` và `crawlSource_test.html`: giữ field phản hồi cũ `是否匹配`, `匹配结果` để không đổi hợp đồng dữ liệu.
- `CrawlParser.java`: giữ dấu hiệu nguồn `正在手打中` dùng để nhận biết chương chưa hoàn tất.
- `crawlSource_add.html`, `crawlSource_update.html`: giữ các token ví dụ `作者`, `状态`, `连载`, `完结`, `分`, `更新` vì đây là rule khớp website nguồn.
- `IpLocationServiceImpl.java`: giữ tên tỉnh/thành Trung Quốc và `中国` làm khóa đầu vào của `ip2region`; kết quả hiển thị đã được ánh xạ sang tiếng Việt.
- `author/book_add.html` ở runtime, green và orange: giữ `玄幻奇幻` trong hidden value tương thích dữ liệu cũ.

## Dữ liệu nội dung mẫu

- `templates/dark/static/**/html/note_*.html`: bài viết mẫu được sinh từ Aspose, không phải giao diện sản phẩm.
- `templates/orange/static/mobile/{index,book_search,book_index,book_detail,book_content}.html` và các bản tương ứng của dark: giữ tên truyện, tác giả, chương và nội dung truyện mẫu. Các nút, nhãn, điều hướng và thông báo trong những file này đã được Việt hóa thủ công.

## SQL

- `doc/sql/*.sql`: migration lịch sử không được sửa; migration Việt hóa mới cần giữ chính xác giá trị nguồn tiếng Trung trong điều kiện `WHERE` để bảo toàn dữ liệu tùy chỉnh và chạy lặp an toàn.

## Thư viện bên thứ ba

- Các thư mục/tệp `layui`, `lay`, `wangEditor`, `layuimini`, `plugins`, `fonts`, `easyui-lang-zh_CN.js`, `layer.m.js` và CSS theme H+ được giữ nguyên để tránh sửa mã nhà cung cấp.

## Ảnh và thương hiệu tích hợp

- Logo runtime, logo theme, ảnh tải bìa và ảnh “máy chủ đang bận” đã được thay bằng tài nguyên ghi “Novel Plus” hoặc thông báo tiếng Việt.
- `pay_wx.png`, `pay_zfb.png`: asset legacy không còn được template/runtime tham chiếu. Tích hợp tạo giao dịch mới chỉ dùng VNPAY; giữ file để không mở rộng đợt Việt hóa sang xóa tài nguyên lịch sử.
- `novel-admin/src/main/resources/static/img/{index.jpg,index_4.jpg,wenku_logo.png}`: ảnh minh họa của theme H+ và tài nguyên Baidu Wenku cũ, không được tham chiếu bởi template/runtime hiện tại.
- Ảnh trong `images/cover` và các ảnh bìa ở theme blue là dữ liệu truyện mẫu, thuộc phạm vi nội dung không tự động dịch.

## Test

- `I18nConfigTest.java` và `MessageCatalogTest.java`: chứa dữ liệu kỳ vọng fallback và token ngoại lệ để tự kiểm tra chính allowlist này.

Mọi ngoại lệ mới phải có lý do kỹ thuật cụ thể, phạm vi đường dẫn tối thiểu và kiểm thử chứng minh chuỗi đó không phải UI first-party.
