# Đề xuất tác phẩm

## Phạm vi

Trang chủ và trang chi tiết sử dụng cùng một dịch vụ đề xuất phía server. Hệ thống không tạo endpoint công khai mới và không gửi lịch sử đọc của tài khoản sang trình duyệt.

Candidate chỉ gồm tác phẩm đang phát hành, đã duyệt nội dung và bìa, có ít nhất một chương, có nội dung và không vượt quá giới hạn tuổi đã xác minh của độc giả. Khách và tài khoản chưa xác minh tuổi chỉ nhận tác phẩm dành cho mọi lứa tuổi.

## Tín hiệu và thứ tự

Điểm sở thích được cộng theo thể loại:

- mua ít nhất một chương của tác phẩm: 8 điểm;
- thêm tác phẩm vào tủ sách: 5 điểm;
- có lịch sử đọc tác phẩm: 3 điểm;
- tại trang chi tiết, thể loại của tác phẩm đang xem: cộng 4 điểm.

Mỗi tác phẩm đã mua chỉ đóng góp một lần dù độc giả mua nhiều chương. Truyện đã có trong tủ sách, lịch sử đọc hoặc lịch sử mua bị loại khỏi danh sách cá nhân hóa. Sau điểm sở thích, hệ thống xếp theo điểm đánh giá, lượt xem, bình luận, thời gian cập nhật và ID để kết quả ổn định. Khách hoặc tài khoản chưa có tín hiệu tự dùng thứ tự phổ biến này làm fallback.

`reader_chapter_event.reader_key_hash` không được dùng để nối với tài khoản. Đây là định danh analytics ẩn danh và chỉ phục vụ số liệu tổng hợp cho tác giả.

## Cache và theme

Khu “Đề xuất nổi bật” của trang chủ được thay bằng “Dành cho bạn” khi có tài khoản đăng nhập. Kết quả cá nhân hóa không được lưu vào cache chung. Cấu hình tác phẩm trang chủ cũ vẫn dùng cache chung nhưng chỉ chứa tác phẩm đã duyệt, dành cho mọi lứa tuổi; khóa cache đã được tăng phiên bản để loại dữ liệu cũ.

Runtime template là lớp nền. Green, orange và dark có template desktop/mobile riêng; blue có desktop riêng và dùng mobile từ runtime nền.

## Kiểm thử

Chạy test mặc định:

```powershell
mvn -pl novel-front -am test
```

Integration MySQL cần database cô lập đã chạy đủ migration:

```powershell
mvn -Dp1.recommendation.mysql.it=true `
  -Dtest=RecommendationMySqlIntegrationTest `
  -Dsurefire.failIfNoSpecifiedTests=false `
  -pl novel-front -am test
```

Test tạo fixture trong transaction và rollback sau khi kiểm tra anonymous/member, giới hạn tuổi, trạng thái kiểm duyệt, loại truyện đã biết và ưu tiên thể loại.
