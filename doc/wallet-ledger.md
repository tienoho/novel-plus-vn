# Ví và sổ cái giao dịch

Khởi Thư dùng sổ cái kép cho mọi biến động Xu. `wallet_account` là nguồn số dư có kiểm toán; `user.account_balance` chỉ là projection tương thích với API/UI cũ và được cập nhật trong cùng transaction database.

## Tài khoản ví

| Chủ sở hữu | Loại tài khoản | Mục đích |
|---|---|---|
| `SYSTEM:0` | `SYSTEM_ISSUANCE` | Tài khoản đối ứng khi phát hành Xu từ nạp tiền hoặc thưởng |
| `SYSTEM:0` | `PLATFORM_REVENUE` | Phần doanh thu nền tảng từ mua chương |
| `SYSTEM:0` | `PAYOUT_CLEARING` | Xu đã giữ cho yêu cầu rút thu nhập đang xử lý |
| `SYSTEM:0` | `REFUND_CLEARING` | Xu đã giữ trong lúc chờ provider xác nhận hoàn VND |
| `USER:<id>` | `READER_XU` | Số Xu khả dụng của độc giả |
| `AUTHOR:<id>` | `AUTHOR_REVENUE_XU` | Doanh thu Xu khả dụng của tác giả |

Tài khoản hệ thống có thể âm để biểu diễn nguồn phát hành. Ví tác giả và ví độc giả trong nghiệp vụ tự nguyện không được âm. Riêng chargeback là sự kiện bắt buộc từ ngân hàng nên được phép đưa ví độc giả xuống âm và chuyển trạng thái ví sang `DEBT`; nạp Xu tiếp theo bù nợ trước và tự đưa ví về `ACTIVE` khi số dư không còn âm.

## Giao dịch và bút toán

- `ledger_transaction`: metadata nghiệp vụ, khóa idempotency, request hash và liên kết giao dịch đảo.
- `wallet_entry`: số tiền ghi Nợ/Có và số dư sau bút toán của từng ví.
- Tổng `wallet_entry.amount` của mỗi giao dịch luôn bằng `0`.
- Mỗi transaction chỉ có tối đa một entry cho một wallet.
- Trigger MySQL chặn `UPDATE`/`DELETE` trên transaction và entry. Sửa sai bằng giao dịch đảo, không sửa lịch sử.

Các loại nghiệp vụ nền hiện có:

| Loại | Bút toán |
|---|---|
| `OPENING_BALANCE` | Tài khoản phát hành → ví độc giả |
| `TOP_UP` | Tài khoản phát hành → ví độc giả |
| `REWARD` | Tài khoản phát hành → ví độc giả |
| `CHAPTER_PURCHASE` | Ví độc giả → ví tác giả + doanh thu nền tảng |
| `AUTHOR_WITHDRAWAL_HOLD` | Ví doanh thu tác giả → clearing chờ payout |
| `REFUND_HOLD` | Ví độc giả → clearing hoàn tiền |
| `REFUND_SETTLED` | Clearing hoàn tiền → tài khoản phát hành sau khi provider xác nhận |
| `REFUND_RELEASE` | Clearing hoàn tiền → ví độc giả khi provider thất bại |
| `CHARGEBACK` | Đảo chính xác giao dịch nạp Xu gốc; ví độc giả có thể thành `DEBT` |

Tỷ lệ chia doanh thu tác giả lấy từ `author.income.share-proportion`; phần Xu lẻ được làm tròn xuống cho tác giả và phần còn lại vào ví nền tảng.

## Idempotency

`ledger_transaction.idempotency_key` là unique. Service lưu thêm SHA-256 của toàn bộ payload bút toán. Gửi lại cùng khóa và cùng payload trả trạng thái đã ghi; dùng cùng khóa cho payload khác bị từ chối.

Khóa hiện tại:

- VNPAY: `VNPAY_TOP_UP:<out_trade_no>`;
- mua chương: `CHAPTER_PURCHASE:<user_id>:<book_index_id>`;
- reward/refund: caller phải cung cấp khóa ổn định từ nghiệp vụ nguồn.

## Hoàn tiền và chargeback

Refund hiện chỉ hỗ trợ toàn phần. Trạng thái được chuyển bằng điều kiện trạng thái trong SQL để hai quản trị viên không thể xử lý đồng thời:

```text
REQUESTED --approve--> APPROVED --provider confirm--> REVERSED
                              \--provider fail-----> FAILED
REQUESTED --reject--------------------------------> REJECTED
```

`APPROVED` chỉ có nghĩa hệ thống đã giữ Xu vào `REFUND_CLEARING`; chưa được coi là đã hoàn VND. Chỉ endpoint xác nhận provider kèm `providerReference` mới chuyển sang `REVERSED` và tất toán clearing. Nếu provider từ chối/thất bại, nhánh `FAILED` trả đủ Xu đã giữ về độc giả.

Chargeback cũng chỉ hỗ trợ toàn phần và đi thẳng tới `REVERSED` sau khi vận hành xác minh sự kiện ngân hàng. Mỗi đơn nạp chỉ có tối đa một refund hoặc chargeback để tránh hoàn trùng. `order_refund_audit` bị trigger chặn sửa/xóa; không cập nhật audit lịch sử để “sửa” trạng thái.

Unique `(user_id, book_index_id)` trong `user_buy_record` và khóa ledger cùng bảo vệ request mua chương lặp.

## Backfill và projection

Migration [sql/20260717_wallet_ledger.sql](sql/20260717_wallet_ledger.sql):

1. tạo các bảng/index/trigger;
2. tạo ví hệ thống, ví độc giả và ví tác giả;
3. chụp `user.account_balance` thành một giao dịch `OPENING_BALANCE` cân bằng;
4. ghi marker `20260717_wallet_ledger_opening_v1` sau khi backfill hoàn tất.

Nếu migration lỗi trước marker, lần chạy sau tiếp tục trên database chưa phục vụ runtime. Sau khi marker tồn tại, migration không tính lại số dư đầu kỳ nên không ghi đè hoạt động tài chính mới.

CRUD user trong admin không được sửa `account_balance`. Mọi nghiệp vụ cấp/trừ Xu mới phải gọi `WalletLedgerService`; không thêm SQL cập nhật số dư trực tiếp.

## Lịch sử độc giả

Endpoint xác thực:

```http
GET /user/wallet/transactions?page=1&limit=20
Authorization: <JWT>
```

`limit` được giới hạn tối đa 100. Endpoint chỉ truy vấn ví của user lấy từ JWT, không nhận `userId` từ client.

## Truy vấn kiểm toán

Tìm giao dịch mất cân bằng:

```sql
SELECT lt.id, lt.transaction_no, SUM(we.amount) AS entry_sum
FROM ledger_transaction lt
JOIN wallet_entry we ON we.ledger_transaction_id = lt.id
GROUP BY lt.id, lt.transaction_no
HAVING SUM(we.amount) <> 0;
```

Tìm projection độc giả bị lệch:

```sql
SELECT u.id, u.account_balance, wa.available_balance
FROM user u
JOIN wallet_account wa
  ON wa.owner_type = 'USER'
 AND wa.owner_id = u.id
 AND wa.account_type = 'READER_XU'
 AND wa.currency = 'XU'
WHERE u.account_balance <> wa.available_balance;
```

Cả hai truy vấn phải trả tập rỗng. Nếu có sai lệch, khóa nghiệp vụ ghi tiền, lưu bằng chứng và điều chỉnh bằng giao dịch ledger được phê duyệt; không sửa transaction/entry cũ.
