# Đồng bộ vị trí đọc, dấu trang và ghi chú

Novel Plus lưu trạng thái đọc riêng tư theo tài khoản để độc giả có thể tiếp tục trên thiết bị khác, đánh dấu một vị trí trong chương và ghi chú trên đoạn văn đã chọn.

## Mô hình dữ liệu

- `reader_progress` có một bản ghi cho mỗi cặp người dùng–tác phẩm. Bản ghi giữ chương gần nhất, chỉ số đoạn gần đúng, character offset tuyệt đối, tỷ lệ đọc và version phía máy chủ.
- `reader_annotation` lưu `BOOKMARK` hoặc `NOTE` theo chương. Mọi truy vấn, cập nhật và xóa đều lọc bằng `user_id`; cột `version` ngăn thiết bị cũ ghi đè ghi chú mới.
- Character offset được tính trên `textContent`, nên không phụ thuộc nội dung chương được biểu diễn bằng thẻ `p`, `br` hay HTML lồng nhau.
- Migration không sửa `user_read_history`; lịch sử đọc cấp chương và trạng thái đọc chi tiết tiếp tục có vòng đời độc lập.

## API

Tất cả endpoint yêu cầu cookie JWT hiện có và chỉ trả dữ liệu của người dùng đang đăng nhập:

- `GET /user/reader-state?bookId=...&bookIndexId=...`
- `PUT /user/reader-state/progress`
- `POST /user/reader-state/annotations`
- `PUT /user/reader-state/annotations/{annotationId}`
- `DELETE /user/reader-state/annotations/{annotationId}?expectedVersion=...`

Service áp dụng cùng policy với trang đọc trước khi trả hoặc ghi trạng thái:

- tác phẩm phải đang xuất bản, đã duyệt nội dung và bìa, không bị gỡ vì bản quyền;
- chương phải thuộc đúng tác phẩm và đã được duyệt;
- tài khoản phải đáp ứng phân loại độ tuổi của tác phẩm;
- với chương VIP, người dùng phải có bản ghi mua chương.

Khi sửa ghi chú, service kiểm tra lại quyền đọc trước khi trả đoạn văn đã chọn. Xóa ghi chú vẫn được phép để người dùng luôn có thể xóa dữ liệu riêng tư của chính mình, kể cả khi tác phẩm bị gỡ sau đó.

## Giao diện đọc

`reader-tools.js` dùng chung cho desktop/mobile và bốn theme cung cấp:

- lưu vị trí hiện tại và tự đồng bộ sau khi dừng cuộn;
- tiếp tục từ character offset đã lưu trong chương hiện tại;
- đánh dấu vị trí đang đọc;
- tạo ghi chú từ vùng văn bản được chọn;
- xem, sửa, xóa và nhảy đến dấu trang/ghi chú;
- phím tắt `Alt+P` lưu vị trí, `Alt+B` đánh dấu và `Alt+N` tạo ghi chú.

Người chưa đăng nhập vẫn đọc/TTS/offline như trước, nhưng các nút đồng bộ bị vô hiệu hóa và không tạo dữ liệu local giả làm dữ liệu đã đồng bộ.

Template trang đọc không được chặn sự kiện `selectstart`, vì thao tác chọn văn bản là đầu vào bắt buộc của ghi chú. Các giới hạn context menu, copy và drag hiện có không được dùng để vô hiệu hóa selection.

## Quyền riêng tư và vận hành

- Dấu trang, ghi chú và đoạn trích là dữ liệu riêng tư; không được đưa vào recommendation, crawler, trang công khai hoặc log nghiệp vụ.
- `selected_text` giới hạn 500 ký tự và `note_text` giới hạn 2.000 ký tự.
- Dữ liệu đồng bộ không được service worker hoặc kho chương offline cache lại.
- Chạy `20260727_reader_annotations.sql` sau migration cộng tác tác giả; migration có thể chạy lặp.

## Kiểm tra

```powershell
mvn -pl novel-front -am -Dtest=ReaderStateServiceImplTest,ReaderStatePackagingTest `
  -Dsurefire.failIfNoSpecifiedTests=false test
node scripts/verify-i18n.mjs
git diff --check
```

Ngày 28/07/2026, bộ reader-state đạt 15/15 test service/packaging và `ReaderStateMySqlIntegrationTest` đạt 1/1 trên MySQL thật, không skip. Integration xác minh upsert progress, version conflict, dữ liệu riêng theo tài khoản, ràng buộc loại annotation và rollback sạch fixture `9973...`.

Smoke runtime đã xác minh đăng nhập bằng cookie, lưu progress, tạo/hiển thị/sửa/xóa ghi chú, tạo/xóa bookmark, reload, panel desktop/mobile và cách ly user A/B. Toàn bộ fixture `9974...`, gồm analytics event append-only, được dọn sạch; trigger bất biến được tái tạo và đối chiếu sau cleanup.

Regression service hiện kiểm tra cả hai chiều của quyền truy cập: chương VIP chưa mua bị chặn nhưng
đã mua được phép đọc trạng thái; nội dung 18+ chặn hồ sơ chưa xác minh nhưng cho phép người lớn đã
xác minh; tác phẩm bị ẩn/gỡ, chương chưa duyệt và ID chương thuộc truyện khác đều không làm lộ dữ
liệu. Test stale version cũng xác nhận thiết bị cũ không ghi đè ghi chú mới.

Ngày 30/07/2026, smoke test chạy trực tiếp từ `novel-front.zip` trên MySQL/Redis cô lập đã xác minh
chương dài 240 đoạn: lưu ở đoạn 237 (`character_offset=28095`, `progress_percent=98.77`), mở lại
chương ở đầu trang và khôi phục tới vùng cuối chương. Phông serif và giãn dòng `2.2` vẫn được áp
dụng sau navigation mới; console không có lỗi. Request dùng User-Agent iPhone cũng render template
mobile, có viewport mobile và nạp cùng `reader-tools.js`.

Smoke test này phát hiện ID lấy từ input HTML đã bị ép sang JavaScript `Number`, trong khi API cố ý
trả Java `Long` dưới dạng chuỗi để không mất độ chính xác. Reader tools nay giữ `bookId` và
`bookIndexId` ở dạng chuỗi từ DOM tới request; test đóng gói cấm đưa `Number(valueOf(...))` trở lại
và đối chiếu năm bản script nền/theme bằng SHA-256.

Chưa thay thế kiểm thử trên thiết bị vật lý và chưa tái chạy bằng JDK 21 trong lượt này.
