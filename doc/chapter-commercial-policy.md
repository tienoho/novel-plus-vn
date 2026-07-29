# Giá chương, tự mở khóa và sự kiện miễn phí

## Mô hình dữ liệu

`book_index.is_vip` tiếp tục xác định chương thu phí và `book_index.book_price` tiếp tục là giá Xu authoritative của luồng mua. Bảng `chapter_commercial_policy` chỉ lưu phần cấu hình tùy chọn theo quan hệ 1-1:

- `custom_price`: giá tác giả đặt; để trống thì giá được tính từ số chữ và luôn tối thiểu 1 Xu đối với chương thu phí;
- `unlock_at`: từ mốc này chương trở thành miễn phí vĩnh viễn;
- `free_from`, `free_until`: cửa sổ miễn phí tạm thời theo khoảng `[free_from, free_until)`;
- `version`: tăng sau mỗi lần ghi đè chính sách để hỗ trợ kiểm toán và chẩn đoán vận hành.

Bản nháp lưu cả giá preview và các trường chính sách. Chính sách chỉ được ghi vào chương công khai trong cùng transaction xuất bản; autosave không làm thay đổi quyền đọc của chương hiện tại.

## Quy tắc truy cập

Một dịch vụ trung tâm được dùng bởi trang đọc, reader-state và luồng mua:

1. Chương không thu phí hoặc đã đến `unlock_at` là miễn phí vĩnh viễn.
2. Chương nằm trong `[free_from, free_until)` là miễn phí tạm thời.
3. Bản ghi mua hợp lệ luôn cho phép đọc.
4. Các trường hợp còn lại phải mua bằng giá khóa từ `book_index.book_price`.

Chỉ chương miễn phí vĩnh viễn được lưu offline. Chương đang miễn phí theo sự kiện và chương VIP đã mua không tạo snapshot offline, tránh giữ nội dung sau khi entitlement thời hạn kết thúc.

## An toàn giao dịch

`POST /user/buyBookIndex` giữ nguyên hợp đồng cũ. Service khóa hàng `book_index`, đọc lại policy và lịch sử mua, sau đó mới quyết định ghi sổ cái. Giá, tên chương và tác phẩm không lấy từ dữ liệu do trình duyệt gửi. Nếu chương vừa chuyển sang miễn phí, request mua trở thành no-op thành công và không ghi sổ cái hay `user_buy_record`.

Mọi khoản mua thực sự vẫn đi qua sổ cái kép với idempotency key `CHAPTER_PURCHASE:<userId>:<bookIndexId>`. Không có đường cập nhật trực tiếp `user.account_balance` ngoài projection của sổ cái.

## API tác giả

- `GET /author/chapter-commercial-policy/{indexId}`: trả cấu hình hiện tại sau khi kiểm tra quyền `MANAGE_CHAPTERS`.
- Autosave draft nhận thêm `customPrice`, `unlockAt`, `freeFrom`, `freeUntil`; endpoint và các field cũ giữ nguyên.
- Chỉ tác giả/chủ thể có quyền `PUBLISH_CHAPTERS` mới có thể làm chính sách có hiệu lực qua xuất bản.

Giá tùy chỉnh phải từ 1 đến 1.000.000 Xu. Cửa sổ miễn phí phải có đủ hai đầu và thời điểm kết thúc phải sau thời điểm bắt đầu. Các ràng buộc này được kiểm tra ở cả service và MySQL.

## Kiểm tra vận hành

```powershell
mvn -Dtest=ChapterCommercialPolicyServiceImplTest,UserChapterPurchaseServiceTest `
  -Dsurefire.failIfNoSpecifiedTests=false -pl novel-front -am test

mvn -Dchapter.commercial.mysql.it=true `
  -Dtest=ChapterCommercialPolicyMySqlIntegrationTest `
  -Dsurefire.failIfNoSpecifiedTests=false -pl novel-front -am test
```

Integration MySQL cần database cô lập đã chạy `20260728_chapter_commercial_policy.sql`. Phải kiểm tra các mốc ngay trước/bằng/sau `unlock_at`, ngay trước/bằng/sau `free_from` và `free_until`, request mua lặp, đổi chính sách đồng thời và marker offline ở desktop/mobile.
