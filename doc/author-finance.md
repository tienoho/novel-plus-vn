# KYC và tài chính tác giả

Nền tảng tài chính tác giả gồm hồ sơ KYC đã mã hóa, ví doanh thu, yêu cầu rút có idempotency, snapshot tài khoản ngân hàng và audit trail bất biến. Đây là nền kỹ thuật; việc xác minh danh tính, khấu trừ thuế và chuyển khoản production vẫn cần quy trình pháp lý/nhà cung cấp được phê duyệt.

## Trạng thái an toàn mặc định

```dotenv
PII_ENCRYPTION_KEY=
AUTHOR_PAYOUT_ENABLED=false
```

KYC từ chối lưu nếu khóa PII chưa hợp lệ. Yêu cầu rút bị tắt nếu `AUTHOR_PAYOUT_ENABLED=false`. Không bật payout chỉ để thử giao diện trên production.

Tạo khóa AES-256 dạng Base64:

```bash
openssl rand -base64 32
```

Lưu khóa trong secret manager. Mất khóa đồng nghĩa không thể giải mã dữ liệu KYC hiện hữu; lộ khóa yêu cầu quy trình xoay khóa và đánh giá sự cố dữ liệu cá nhân.

## Bảo vệ dữ liệu

- Họ tên pháp lý, ngày sinh, CCCD/hộ chiếu, mã số thuế, số tài khoản và tên chủ tài khoản dùng AES-256-GCM với IV ngẫu nhiên.
- CCCD/hộ chiếu và tài khoản ngân hàng có HMAC-SHA256 để phát hiện trùng mà không so sánh plaintext.
- API trạng thái chỉ trả bốn số cuối.
- Yêu cầu rút chụp ciphertext ngân hàng tại thời điểm tạo để thay đổi KYC sau đó không sửa lịch sử payout.
- `author_kyc_audit` và `author_withdrawal_audit` bị trigger chặn update/delete.
- Không log request body KYC, ciphertext hoặc khóa mã hóa.

## API tác giả

Tất cả endpoint lấy author/user từ JWT; client không được truyền ID chủ sở hữu.

```http
POST /author/finance/kyc
GET  /author/finance/kyc
GET  /author/finance/revenue-balance
POST /author/finance/withdrawals
```

Phiên bản chấp thuận hiện tại là `KYC-VN-2026-01`. CCCD yêu cầu 12 chữ số; hộ chiếu cho phép 6–12 ký tự chữ/số; tác giả phải đủ 18 tuổi. Đây là validation kỹ thuật, không thay thế xác minh từ nguồn tin cậy.

Ví dụ yêu cầu rút:

```json
{
  "amountXu": 100000,
  "idempotencyKey": "withdrawal-<author>-<cycle>-<request>"
}
```

Khóa idempotency phải ổn định cho cùng một thao tác. Retry trả lại cùng yêu cầu, không giữ Xu lần hai.

## Cấu hình payout

| Biến | Mặc định | Ý nghĩa |
|---|---:|---|
| `AUTHOR_PAYOUT_ENABLED` | `false` | Công tắc nhận yêu cầu rút |
| `AUTHOR_PAYOUT_VND_PER_XU` | `10` | Tỷ giá chốt vào yêu cầu |
| `AUTHOR_PAYOUT_MIN_XU` | `100000` | Mức rút tối thiểu |
| `AUTHOR_PAYOUT_REQUEST_OPEN_DAY` | `1` | Ngày đầu kỳ nhận yêu cầu |
| `AUTHOR_PAYOUT_REQUEST_CLOSE_DAY` | `10` | Ngày cuối kỳ nhận yêu cầu, tối đa 28 |

Giá trị mặc định không phải kết luận về thuế hay hợp đồng. Trước production phải phê duyệt tỷ giá, mức tối thiểu, lịch chốt và cách tính thuế bằng chính sách kinh doanh/pháp lý chính thức.

## Luồng yêu cầu rút

1. Tác giả phải có KYC `VERIFIED`.
2. Request phải nằm trong kỳ, đạt mức tối thiểu và không vượt số dư ví tác giả.
3. Hệ thống snapshot ngân hàng/tỷ giá, tạo yêu cầu `PENDING_REVIEW`.
4. Ledger ghi `AUTHOR_WITHDRAWAL_HOLD`: trừ ví tác giả và cộng `PAYOUT_CLEARING`.
5. Review/payout provider xử lý trạng thái tiếp theo.
6. Khi từ chối/hủy, hệ thống phải tạo giao dịch đảo hold; không sửa entry cũ.

Hiện repository đã hoàn thành bước 1–4. `MANUAL_BANK` chỉ là nhãn provider an toàn mặc định; chưa có thao tác admin duyệt/chuyển khoản production. Giữ `AUTHOR_PAYOUT_ENABLED=false` cho tới khi bước 5–6 và đối soát ngân hàng được triển khai, kiểm thử.

## Migration và kiểm toán

Migration [sql/20260718_author_payout.sql](sql/20260718_author_payout.sql) tạo:

- `author_kyc_profile`;
- `author_kyc_audit`;
- `author_withdrawal_request`;
- `author_withdrawal_audit`.

Kiểm tra plaintext không xuất hiện trong cột mã hóa và mọi yêu cầu có audit:

```sql
SELECT id, author_id, status, identity_type,
       identity_number_last4, bank_code, bank_account_last4
FROM author_kyc_profile;

SELECT wr.withdrawal_no
FROM author_withdrawal_request wr
LEFT JOIN author_withdrawal_audit wa
  ON wa.withdrawal_request_id = wr.id
WHERE wa.id IS NULL;
```

Không truy vấn hoặc xuất ciphertext vào dashboard/log. Chỉ service có quyền giải mã mới được đọc PII rõ, với audit truy cập riêng ở bước quản trị tiếp theo.
