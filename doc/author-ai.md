# AI hỗ trợ sáng tác có bảo vệ bản thảo

## Phạm vi

Tác giả và cộng tác viên có quyền quản lý chương có thể mở rộng, rút gọn, viết tiếp hoặc trau chuốt đoạn văn. Request bắt buộc dùng `POST` với JSON body; nội dung bản thảo không được đặt trong URL, query string, cache hoặc log first-party.

AI nhận tối đa 20.000 ký tự nguồn và tối đa 6.000 ký tự từ 20 mục story bible đầu tiên mà người gọi có quyền đọc. Prompt phân tách story bible và đoạn nguồn thành vùng dữ liệu, yêu cầu model bỏ qua chỉ dẫn nằm trong nội dung đó.

## API

- `POST /author/ai/expand`
- `POST /author/ai/condense`
- `POST /author/ai/continue`
- `POST /author/ai/polish`

Body dùng các trường `bookId`, `draftId` không bắt buộc, `text`, `ratio` hoặc `length`. Mở rộng nhận tỷ lệ trên 100% đến 500%; rút gọn nhận từ 1% đến dưới 100%; viết tiếp nhận 1–4.000 ký tự.

Mọi endpoint xác minh đăng nhập tác giả, trạng thái tài khoản, quyền `MANAGE_CHAPTERS`, và quyền sở hữu draft nếu có `draftId`. Story bible chỉ được đưa vào prompt khi người gọi có quyền `MANAGE_STORY`.

## Provenance và quyền riêng tư

`author_ai_usage` là audit append-only. Mỗi lần trả nội dung thành công phải ghi:

- tác phẩm, chủ sở hữu, tác giả thực hiện và draft liên quan;
- thao tác, model, số ký tự và số mục ngữ cảnh;
- SHA-256 của đầu vào và đầu ra.

Bảng không có cột lưu văn bản nguồn, story bible hay kết quả AI. Trigger từ chối `UPDATE` và `DELETE`. Nếu không ghi được provenance, service không trả đầu ra cho client.

Nội dung nguồn và ngữ cảnh vẫn được gửi tới nhà cung cấp AI đã cấu hình; đơn vị vận hành phải chọn hợp đồng/API có chính sách lưu trữ phù hợp và thông báo cho tác giả trước khi bật `OPENAI_API_KEY` thật.

## Vận hành

Chạy `20260728_author_ai.sql` trước image ứng dụng mới. Migration có thể chạy lặp và tái tạo hai trigger bất biến. Không bật AI trong production khi chưa duyệt chính sách xử lý bản thảo của nhà cung cấp.

## Xác minh

Ngày 28/07/2026, service và packaging đạt 8/8 test. Migration chạy lặp hai lần trên MySQL 8.4 thật; đối chiếu có một bảng, hai trigger bất biến và không có cột `source_text`, `output_text` hoặc `story_context`. Full Maven reactor đạt 280 test, không failure/error; verifier i18n/JS đạt 183 file JavaScript và 333 khối inline.

Image front được build lại và container healthy. POST không đăng nhập trả mã nghiệp vụ `1001`; URL SSE cũ chỉ đi vào trang không tìm thấy, không gọi model. Chưa smoke đầu ra từ nhà cung cấp AI thật vì môi trường không có credential được phê duyệt. Browser nội bộ không có phiên tác giả phù hợp nên tương tác toolbar sau đăng nhập chưa được tái xác minh trong lượt này; fixture smoke tạm thời đã được xóa sạch.
