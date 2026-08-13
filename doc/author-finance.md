# KYC và tài chính tác giả

Nền tảng tài chính tác giả gồm hồ sơ KYC đã mã hóa, ví doanh thu, yêu cầu rút có idempotency, snapshot tài khoản ngân hàng và audit trail bất biến. Đây là nền kỹ thuật; việc xác minh danh tính, khấu trừ thuế và chuyển khoản production vẫn cần quy trình pháp lý/nhà cung cấp được phê duyệt.

## Trạng thái an toàn mặc định

```dotenv
PII_ENCRYPTION_KEY=
AUTHOR_PAYOUT_ENABLED=false
FINANCIAL_VOUCHER_ISSUANCE_ENABLED=false
PLATFORM_LEGAL_NAME=
PLATFORM_TAX_CODE=
```

KYC từ chối lưu nếu khóa PII chưa hợp lệ. Yêu cầu rút bị tắt nếu `AUTHOR_PAYOUT_ENABLED=false`. Phát hành chứng từ mới bị tắt nếu thiếu cờ, tên pháp nhân hoặc mã số thuế hợp lệ. Không bật payout/chứng từ chỉ để thử giao diện trên production.

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
| `FINANCIAL_VOUCHER_ISSUANCE_ENABLED` | `false` | Công tắc phát hành biên nhận/phiếu chi mới |
| `PLATFORM_LEGAL_NAME` | rỗng | Tên pháp nhân đã xác minh |
| `PLATFORM_TAX_CODE` | rỗng | Mã số thuế pháp nhân đã xác minh |

Giá trị mặc định không phải kết luận về thuế hay hợp đồng. Trước production phải phê duyệt tỷ giá, mức tối thiểu, lịch chốt và cách tính thuế bằng chính sách kinh doanh/pháp lý chính thức. Không dùng file “chứng từ” do hệ thống xuất như hóa đơn điện tử nếu chưa tích hợp nhà cung cấp hóa đơn và hoàn tất thủ tục pháp lý tương ứng.

## Định dạng PDF chứng từ

PDF chứng từ được tạo bằng PDFBox `3.0.8` và nhúng trực tiếp Open Sans `6.1.0` Regular/Bold để
hiển thị tiếng Việt độc lập với font cài trên máy chạy. Số tiền dùng định dạng `vi-VN`; thời gian phát
hành dùng múi giờ `Asia/Ho_Chi_Minh`. Nội dung dài được ngắt dòng và phân trang thay vì tràn khỏi
khổ A4.

Font Open Sans được phân phối theo Open Font License; bản giấy phép nằm trong dependency tại
`fonts/ttf/OpenSans/OFL.txt` và được đóng gói cùng fat JAR. Nếu thiếu font hoặc PDFBox không thể tạo
tài liệu hợp lệ, service ném lỗi và không trả một tệp giả có tiền tố `%PDF`; endpoint vì vậy thất bại
đóng thay vì phát hành chứng từ hỏng. Test hồi quy mở lại PDF, trích xuất các nhãn tiếng Việt và xác
nhận mọi font được sử dụng đều đã nhúng.

## Phân quyền chứng từ tác giả

Các API `/author/finance/receipts`, chi tiết, PDF, JSON và CSV luôn lọc theo `author_id` lấy từ phiên
đăng nhập. Ownership được xác định qua
`financial_voucher.reference_id → author_withdrawal_request.id → author_id`; không dùng bút danh vì
bút danh có thể trùng hoặc thay đổi. Chứng từ không thuộc tác giả hiện tại được xử lý giống chứng từ
không tồn tại, không để lộ metadata cho phép dò mã.

## Luồng yêu cầu rút

1. Tác giả phải có KYC `VERIFIED`.
2. Request phải nằm trong kỳ, đạt mức tối thiểu và không vượt số dư ví tác giả.
3. Hệ thống snapshot ngân hàng/tỷ giá, tạo yêu cầu `PENDING_REVIEW`.
4. Ledger ghi `AUTHOR_WITHDRAWAL_HOLD`: trừ ví tác giả và cộng `PAYOUT_CLEARING`.
5. Quản trị viên có quyền approve nhập thuế khấu trừ và chuyển yêu cầu sang `APPROVED`; một quản trị viên khác có quyền execute mới được nhận xử lý và chuyển sang `PROCESSING`.
6. Chính executor đã nhận xử lý ghi mã tham chiếu ngân hàng; yêu cầu chuyển sang `SETTLEMENT_PENDING`.
7. Worker tất toán `PAYOUT_CLEARING` bằng giao dịch `AUTHOR_WITHDRAWAL_SETTLED`, sau đó chuyển yêu cầu sang `PAID`.
8. Khi từ chối hoặc chuyển khoản thất bại, yêu cầu chuyển sang `RELEASE_PENDING`; worker tạo giao dịch đảo hold rồi kết thúc ở `REJECTED`, `FAILED` hoặc `CANCELLED`. Không sửa entry cũ.

Repository đã triển khai đầy đủ state machine quản trị, release và settlement ở trên. `MANUAL_BANK` vẫn chỉ là provider thủ công: hệ thống ghi nhận thao tác và mã tham chiếu nhưng chưa tự gọi API ngân hàng hoặc tự đối soát sao kê. Giữ `AUTHOR_PAYOUT_ENABLED=false` trong production cho tới khi quy trình chuyển khoản, phân quyền người duyệt, nguyên tắc bốn mắt, đối soát ngân hàng và chính sách thuế được phê duyệt, kiểm thử.

## Quản trị và phân quyền

Trang `/novel/authorFinance` dùng các quyền tách biệt:

| Quyền | Phạm vi |
|---|---|
| `novel:authorFinance:view` | Xem danh sách KYC và yêu cầu rút đã che dữ liệu nhạy cảm |
| `novel:authorFinance:pii` | Giải mã KYC hoặc snapshot tài khoản nhận tiền; mỗi lần xem đều ghi audit |
| `novel:authorFinance:kyc` | Duyệt hoặc từ chối KYC |
| `novel:authorFinance:payout:approve` | Duyệt số tiền/thuế hoặc từ chối yêu cầu chưa chuyển khoản |
| `novel:authorFinance:payout:execute` | Nhận xử lý payout và ghi nhận thành công/thất bại |

Các endpoint quản trị chính:

```http
GET  /novel/authorFinance/kyc/list
GET  /novel/authorFinance/kyc/{id}
POST /novel/authorFinance/kyc/{id}/approve
POST /novel/authorFinance/kyc/{id}/reject
GET  /novel/authorFinance/withdrawals/list
GET  /novel/authorFinance/withdrawals/{id}/payout-details
POST /novel/authorFinance/withdrawals/{id}/approve
POST /novel/authorFinance/withdrawals/{id}/processing
POST /novel/authorFinance/withdrawals/{id}/paid
POST /novel/authorFinance/withdrawals/{id}/failed
POST /novel/authorFinance/withdrawals/{id}/reject
```

Không cấp quyền `pii`, `payout:approve` hoặc `payout:execute` chỉ vì người dùng có quyền sửa hồ sơ tác giả. Một tài khoản có thể được gán cả hai quyền để dự phòng nhưng backend và database vẫn từ chối tài khoản đó tự approve rồi execute cùng yêu cầu. Tài khoản vận hành phải dùng người dùng riêng; không dùng tài khoản seed `admin/admin` trong production.

## Migration và kiểm toán

Migration [sql/20260718_author_payout.sql](sql/20260718_author_payout.sql) tạo:

- `author_kyc_profile`;
- `author_kyc_audit`;
- `author_withdrawal_request`;
- `author_withdrawal_audit`.

Migration [sql/20260817_author_payout_four_eyes.sql](sql/20260817_author_payout_four_eyes.sql) lưu riêng
`approved_by`/`executed_by`, thêm check constraint ngăn hai actor trùng nhau và seed hai quyền approve/execute.
Yêu cầu legacy thiếu `approved_by` bị fail-closed; không suy đoán người duyệt từ `reviewed_by` vì cột legacy
có thể đã bị phase sau ghi đè.

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

Không truy vấn hoặc xuất ciphertext vào dashboard/log. Chỉ service có quyền giải mã mới được đọc PII rõ. Sự kiện `VIEWED_PII` và `VIEWED_PAYOUT_PII` phải xuất hiện trong audit tương ứng mỗi lần quản trị viên xem dữ liệu.

Trước khi bật payout, chạy tối thiểu hai kịch bản trên bản sao dữ liệu kiểm thử:

- từ chối: `PENDING_REVIEW → RELEASE_PENDING → REJECTED`, ví tác giả được hoàn đủ Xu và `PAYOUT_CLEARING` về 0;
- thành công: `PENDING_REVIEW → APPROVED → PROCESSING → SETTLEMENT_PENDING → PAID`, `PAYOUT_CLEARING` về 0 và giao dịch settlement cân bằng.
- bốn mắt: actor A approve; A bị từ chối khi execute; actor B execute thành công; hai executor cạnh tranh chỉ một người nhận được yêu cầu.

Mọi `ledger_transaction` phải có tổng `wallet_entry.amount = 0`. Retry cùng `idempotencyKey` phải trả lại cùng `withdrawal_no`, không tạo hold thứ hai.
