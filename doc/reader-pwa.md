# PWA, đọc offline và TTS tiếng Việt

Novel Plus cung cấp PWA cho khu vực độc giả, chế độ tiết kiệm dữ liệu, kho chương miễn phí đọc offline và TTS tiếng Việt dùng chung trên desktop/mobile.

## Ranh giới bảo mật

- Service worker chỉ lưu asset công khai như CSS, JavaScript, font và ảnh giao diện.
- Mọi navigation HTML luôn dùng mạng; service worker không lưu trang tài khoản, thanh toán, tác giả, API hay HTML chương vào Cache Storage.
- Response trang chương có `Cache-Control: private, no-store, max-age=0` và `Vary: Cookie, Authorization`.
- Khi mất mạng, navigation trả trang `/offline-reader.htm`; trang này đọc snapshot văn bản từ IndexedDB.
- Nút **Lưu offline** chỉ hoạt động khi server render marker cho chương miễn phí. Chương VIP, kể cả chương đã mua, không được lưu bởi luồng offline first-party trong phiên bản này.
- Snapshot chỉ chứa tên truyện, tên chương và văn bản chương; không lưu token, cookie, HTML thực thi hoặc dữ liệu hồ sơ người dùng.

## Sử dụng

Trên trang đọc chương, thanh **Công cụ đọc** cung cấp:

- đọc, tạm dừng, tiếp tục và dừng TTS;
- chọn tốc độ và giọng `vi-VN` nếu hệ điều hành cung cấp;
- chọn họ font phù hợp tiếng Việt, giãn dòng và nền đọc; các lựa chọn được ghi nhớ trên thiết bị;
- lưu chương miễn phí và mở **Thư viện offline**;
- bật/tắt chế độ tiết kiệm dữ liệu.
- đồng bộ vị trí đọc, đánh dấu đoạn và quản lý ghi chú riêng tư khi đã đăng nhập.

Phím tắt không áp dụng khi con trỏ nằm trong ô nhập liệu:

- `Alt+R`: đọc/tạm dừng/tiếp tục;
- `Alt+S`: dừng đọc;
- `Alt+O`: lưu chương miễn phí;
- `Alt+D`: bật hoặc tắt tiết kiệm dữ liệu.
- `Alt+P`: lưu vị trí đọc;
- `Alt+B`: đánh dấu đoạn đang đọc;
- `Alt+N`: tạo ghi chú từ vùng văn bản đã chọn.

Các thao tác cũ trên mobile dùng phần tử `button`, nút biểu tượng có tên truy cập và trang đọc không khóa khả năng phóng to của trình duyệt. Bộ chọn dùng chung có focus hiển thị, trạng thái động `aria-live`, khai báo phím tắt và quan hệ mở/đóng với bảng ghi chú.

Chế độ tiết kiệm dữ liệu tự bật khi `navigator.connection.saveData` được bật. Khi hoạt động, ảnh không thiết yếu được lazy-load, analytics đọc chương và script analytics ngoài hệ thống không được gửi.

## Vận hành và cập nhật

- PWA chỉ đăng ký service worker trên HTTPS hoặc `localhost`.
- Cache shell có version trong `service-worker.js`. Khi thay đổi asset bắt buộc, tăng `CACHE_VERSION` để client dọn cache cũ trong pha activate.
- Nội dung offline nằm trong IndexedDB `novel-reader-offline-v1`, object store `chapters`.
- Asset PWA nằm ở runtime base; quy trình đóng gói tiếp tục merge `runtime base → theme overlay`, nên cả bốn theme dùng cùng service worker và offline reader.

## Kiểm tra

```powershell
node scripts/verify-i18n.mjs
mvn -pl novel-front -am test
git diff --check
```

Trong trình duyệt, xác minh manifest và service worker có MIME hợp lệ, thử mất mạng sau khi lưu một chương miễn phí, và xác nhận chương VIP không có khả năng lưu offline.
