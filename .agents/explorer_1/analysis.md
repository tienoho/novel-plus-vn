# Báo cáo Phân tích Kỹ thuật R1 & R2 (Technical Investigation Report)

**Dự án**: Novel-Plus  
**Tác giả**: Explorer 1  
**Ngày thực hiện**: 2026-07-25  
**Phạm vi**: 
- **R1. Payout, Ledger & Financial Workflow** (Sổ cái kép, Payout runtime, Refund, Chargeback, Đối soát ngân hàng, Tự động chuyển khoản tác giả, Tất toán tài chính)
- **R2. Payment Adapter & VietQR/NAPAS Integration** (Kiến trúc Payment Adapter, Giao thức VietQR/NAPAS, QR Code, Webhook/Callback, Nạp/Rút tiền)

---

## 1. Tổng quan Trạng thái Hiện tại (Current State Baseline)

Hệ thống Novel-Plus đã hoàn thiện một phần nền tảng sổ cái kép và quản lý rút thu nhập tác giả thủ công, tuy nhiên chưa có kiến trúc Payment Adapter dùng chung, chưa có giao thức VietQR/NAPAS, chưa có workflow Refund/Chargeback và chưa có hệ thống đối soát ngân hàng tự động.

### 1.1. Hiện trạng R1: Payout, Ledger & Financial Workflow
- **Sổ cái kép (Double-Entry Ledger)**:
  - **Database**: Bảng `wallet_account`, `ledger_transaction`, `wallet_entry`, `platform_migration_history` đã được tạo từ migration `20260717_wallet_ledger.sql`.
  - **Triggers**: Trigger MySQL chặn `UPDATE`/`DELETE` trực tiếp lên `ledger_transaction` và `wallet_entry` để bảo đảm tính bất biến (immutability).
  - **Ví hệ thống & người dùng**: Hỗ trợ ví `SYSTEM_ISSUANCE`, `PLATFORM_REVENUE`, `PAYOUT_CLEARING`, `READER_XU` (độc giả), `AUTHOR_REVENUE_XU` (tác giả).
  - **Service**: `WalletLedgerServiceImpl` thực hiện nạp Xu (`TOP_UP`), mua chương (`CHAPTER_PURCHASE`), thưởng Xu (`REWARD`), đảo bút toán (`reverseTransaction`), giữ Xu rút (`AUTHOR_WITHDRAWAL_HOLD`), tất toán rút (`AUTHOR_WITHDRAWAL_SETTLED`).
  - **Idempotency**: Dùng `idempotency_key` unique + SHA-256 request hash kiểm tra payload.
- **Tài chính tác giả & Runtime Payout**:
  - **Database**: Bảng `author_kyc_profile`, `author_kyc_audit`, `author_withdrawal_request`, `author_withdrawal_audit` từ migration `20260718_author_payout.sql`.
  - **Bảo mật PII**: Mã hóa AES-256-GCM cho thông tin cá nhân/tài khoản ngân hàng, HMAC-SHA256 để kiểm tra trùng lặp.
  - **State Machine Rút tiền**: `PENDING_REVIEW` -> `APPROVED` -> `PROCESSING` -> `SETTLEMENT_PENDING` -> `PAID` (hoặc `RELEASE_PENDING` -> `REJECTED`/`FAILED`/`CANCELLED`).
  - **Worker/Schedule**: `AuthorWithdrawalLifecycleSchedule` & `AuthorWithdrawalLifecycleServiceImpl` chạy định kỳ để xử lý `SETTLEMENT_PENDING` và `RELEASE_PENDING`.
- **Hạn chế / Điểm thiếu hụt của R1**:
  - **Refund & Chargeback**: Chưa có bảng DB (`order_refund`, `chargeback_record`), chưa có API hoặc Service quản lý quy trình yêu cầu/duyệt hoàn tiền hoặc xử lý khiếu nại chargeback từ ngân hàng.
  - **Đối soát ngân hàng (Bank Reconciliation)**: Mới chỉ có scheduler `VnpayReconciliationSchedule` tự đối soát qua API QueryDr của VNPAY. Chưa có công cụ nhập sao kê/file đối soát ngân hàng (CSV/MT940/Excel) và engine so khớp giao dịch thực tế.
  - **Tự động chuyển khoản (Auto-transfer for Authors)**: `payout_provider` hiện đang cố định là `MANUAL_BANK`. Quản trị viên phải tự thực hiện chuyển khoản ngoài và gõ mã tham chiếu thủ công.
  - **Kiểm toán số dư tự động**: Chưa có Scheduled Task hoặc Endpoint trong Admin để chủ động phát hiện lệch sổ cái (`SUM(amount) != 0` hoặc `user.account_balance != wallet_account.available_balance`).
  - **Vấn đề toàn vẹn**: Bảng `PayController` trong `novel-admin` hiện cho phép thao tác trực tiếp Sửa/Xóa đơn hàng `order_pay`, có nguy cơ làm sai lệch sổ cái kép nếu quản trị viên sử dụng.

### 1.2. Hiện trạng R2: Payment Adapter & VietQR/NAPAS Integration
- **Hệ thống thanh toán hiện có**:
  - Mới chỉ có `VnpayService` và `PayController` phục vụ duy nhất cổng VNPAY (`pay_channel = 4`).
  - Đã loại bỏ Alipay / WeChatPay khỏi giao diện tạo đơn mới, chỉ giữ lại dữ liệu lịch sử.
- **Hạn chế / Điểm thiếu hụt của R2**:
  - **Kiến trúc Payment Adapter**: Chưa có interface `PaymentAdapter` hoặc Factory/Registry dùng chung. Code VNPAY được gọi trực tiếp từ `PayController` với mã hằng số `4`.
  - **Tích hợp VietQR / NAPAS**: Hoàn toàn **chưa có dòng code nào** triển khai VietQR hoặc NAPAS trong toàn bộ ứng dụng Java.
  - **Chuẩn VietQR**: Chưa có tiện ích sinh chuỗi QR EMVCo (Payload Format `000201`, GUID `A000000727`, BIN ngân hàng, số tài khoản, mã đơn hàng `outTradeNo`, checksum CRC16 CCITT-FALSE) hoặc VietQR Quick Link URL.
  - **Luồng Nạp/Rút VietQR & NAPAS**: Chưa có webhook/callback tiếp nhận thông báo nạp tiền qua ngân hàng/VietQR, chưa có API gọi chuyển khoản nhanh NAPAS 247 phục vụ tự động chi trả cho tác giả.

---

## 2. Chi tiết các Thành phần Cần bổ sung & Sửa đổi

Để đạt 100% Acceptance Criteria cho R1 và R2, cần xây dựng các thành phần theo thiết kế chi tiết dưới đây:

### 2.1. Thiết kế Mô-đun cho R1: Payout, Ledger & Financial Workflow

#### A. Quy trình Refund & Chargeback Workflow
1. **Database Schema**: Tạo bảng `order_refund`
   - Quản lý các mã `refund_no`, mã đơn gốc `out_trade_no`, `user_id`, số tiền VND, số Xu tương ứng.
   - Phân loại `type`: `REFUND` (hoàn tiền yêu cầu) hoặc `CHARGEBACK` (ngân hàng thu hồi tiền).
   - Trạng thái: `REQUESTED` -> `APPROVED` / `REJECTED` -> `REVERSED`.
   - Lưu vết `original_ledger_transaction_id` và `reversal_ledger_transaction_id`.
2. **Logic Nghiệp vụ**:
   - `RefundService`: Kiểm tra điều kiện hoàn tiền (đơn gốc thành công, ví độc giả còn đủ số Xu nạp hoặc xử lý trừ âm/khóa tính năng nếu Xu đã bị chi tiêu).
   - Gọi `WalletLedgerService.reverseTransaction()` để tạo giao dịch đảo sổ cái kép (Nợ ví độc giả / Có ví phát hành `SYSTEM_ISSUANCE`).
   - Cập nhật trạng thái `order_pay` hoặc ghi nhận thông tin refund.
3. **API Endpoints**:
   - `POST /user/refund/request` (`novel-front`): Độc giả gửi yêu cầu hoàn tiền.
   - `GET /novel/finance/refunds/list` (`novel-admin`): Quản trị viên xem danh sách yêu cầu hoàn tiền / chargeback.
   - `POST /novel/finance/refunds/{id}/approve` (`novel-admin`): Duyệt hoàn tiền và kích hoạt giao dịch đảo.
   - `POST /novel/finance/refunds/{id}/reject` (`novel-admin`): Từ chối hoàn tiền kèm lý do.
   - `POST /novel/finance/chargeback/record` (`novel-admin`): Ghi nhận giao dịch Chargeback từ ngân hàng.

#### B. Hệ thống Đối soát Ngân hàng (Bank Reconciliation Engine)
1. **Database Schema**: Tạo bảng `bank_reconciliation_batch` và `bank_reconciliation_item`.
2. **Engine So khớp**:
   - Phân tích file sao kê ngân hàng / đối soát cổng (định dạng CSV / Excel / JSON).
   - Đối chiếu theo `out_trade_no` hoặc `trade_no` với `order_pay` và `ledger_transaction`.
   - Phân loại kết quả giao dịch: `MATCHED` (Khớp), `AMOUNT_MISMATCH` (Lệch số tiền), `NOT_FOUND_IN_SYSTEM` (Có ở ngân hàng nhưng thiếu ở hệ thống), `NOT_FOUND_IN_BANK` (Có ở hệ thống nhưng thiếu ở ngân hàng).
3. **API Endpoints**:
   - `POST /novel/finance/reconciliation/upload` (`novel-admin`): Tải lên file sao kê ngân hàng.
   - `GET /novel/finance/reconciliation/batches` (`novel-admin`): Tra cứu lịch sử đối soát và chi tiết các giao dịch bất thường.

#### C. Tự động Chuyển khoản Tác giả (Auto-transfer for Authors)
1. **Cấu hình Payout Provider**: Bổ sung hỗ trợ provider `VIETQR_NAPAS_247` bên cạnh `MANUAL_BANK`.
2. **Tự động tất toán**: Khi yêu cầu rút tiền được Admin phê duyệt (`APPROVED`), hệ thống gọi adapter chi tiền NAPAS 247 / VietQR API.
3. **Cập nhật trạng thái**: Khi nhận phản hồi chuyển khoản thành công từ ngân hàng, tự động cập nhật `provider_reference`, chuyển trạng thái sang `SETTLEMENT_PENDING`, và để worker tất toán sổ cái (`AUTHOR_WITHDRAWAL_SETTLED`) chuyển về `PAID`.

#### D. Công cụ Tự động Kiểm tra Toàn vẹn Sổ cái (Ledger Integrity Audit)
1. **Scheduled Job**: `LedgerIntegrityCheckSchedule` chạy hàng ngày hoặc theo chu kỳ.
2. **Nội dung kiểm tra**:
   - Kiểm tra `SUM(wallet_entry.amount) != 0` trên tất cả `ledger_transaction`.
   - Kiểm tra lệch projection `user.account_balance != wallet_account.available_balance`.
   - Cảnh báo qua Log/Monitoring nếu phát hiện bất kỳ sai lệch nào.
3. **Vấn đề Sửa/Xóa Admin**: Khóa tính năng Sửa/Xóa đơn nạp tiền trong `PayController` của `novel-admin` hoặc yêu cầu thao tác qua quy trình Hoàn tiền/Đảo sổ cái.

---

### 2.2. Thiết kế Mô-đun cho R2: Payment Adapter & VietQR/NAPAS Integration

#### A. Kiến trúc Payment Adapter Dùng chung
1. **Core Interface**: `PaymentAdapter` nằm trong `novel-common` (hoặc `novel-front` package `service.payment`):
   ```java
   public interface PaymentAdapter {
       byte getChannelCode();
       String getChannelName();
       PaymentCreationResult createDepositOrder(PaymentCreationRequest request);
       WebhookVerifyResult verifyAndParseWebhook(Map<String, String> headers, Map<String, String> params, String body);
       QueryOrderResult queryOrderStatus(String outTradeNo);
       PayoutResult processPayout(PayoutRequest request);
   }
   ```
2. **Registry / Factory Pattern**: `PaymentAdapterFactory` tự động đăng ký các bean triển khai `PaymentAdapter` (VNPAY, VietQR, NAPAS) và định tuyến theo `payChannel`.
3. **Chuyển đổi VNPAY**: Tái cấu trúc `VnpayService` triển khai `PaymentAdapter`.

#### B. Giao thức VietQR / NAPAS
1. **Mã kênh thanh toán**: Đăng ký `pay_channel = 5` cho VietQR / NAPAS Deposit.
2. **Tiện ích Chuẩn VietQR (EMVCo QR Generator)**:
   - Viết `VietQrGeneratorUtil` sinh chuỗi VietQR chuẩn EMVCo:
     - Tag 00: `01`
     - Tag 01: `12` (Dynamic QR)
     - Tag 38: Subtag 00 (`A000000727`), Subtag 01 (Bank BIN, ví dụ `970422`), Subtag 02 (Số tài khoản)
     - Tag 53: `704` (VND)
     - Tag 54: Số tiền VND
     - Tag 58: `VN`
     - Tag 62: Subtag 08 (Nội dung chuyển khoản / Mã đơn `outTradeNo`)
     - Tag 63: Mã kiểm tra CRC16 (Polynomial 0x1021, Init 0xFFFF)
   - Hỗ trợ VietQR Quick Link URL Format làm giải pháp dự phòng hiển thị ảnh QR code nhanh.
3. **Xử lý Webhook / Callback Nạp tiền VietQR**:
   - Controller `/pay/vietqr/webhook` tiếp nhận thông báo biến động số dư từ VietQR / Ngân hàng đối tác.
   - Xác thực chữ ký Secret / Token header.
   - Bóc tách nội dung chuyển khoản để lấy mã đơn `outTradeNo`.
   - Gọi `OrderService.processPayOrder` và `WalletLedgerService.creditReaderTopUp` với tính năng chống ghi trùng (Idempotent).
4. **Giao thức Rút tiền Chi trả Tác giả NAPAS 247**:
   - `NapasPayoutAdapter`: Gọi API chuyển khoản nhanh 24/7 NAPAS sang tài khoản ngân hàng tác giả dựa trên thông tin KYC (Bank BIN, Bank Account Number, Account Name).
   - Tiếp nhận mã giao dịch ngân hàng và tự động cập nhật tiến trình Rút tiền tác giả.

---

## 3. Thay đổi CSDL Cần thiết (Database Schema Changes)

Cần tạo migration script `doc/sql/20260720_refund_reconciliation_vietqr.sql`:

```sql
-- Migration cho R1 (Refund, Chargeback, Reconciliation) và R2 (VietQR/NAPAS Payment Adapter)
SET NAMES utf8mb4;

-- 1. Bảng quản lý Hoàn tiền và Chargeback
CREATE TABLE IF NOT EXISTS `order_refund` (
    `id`                             bigint(20)   NOT NULL AUTO_INCREMENT,
    `refund_no`                      varchar(64)  NOT NULL COMMENT 'Mã yêu cầu hoàn tiền',
    `out_trade_no`                   bigint(20)   NOT NULL COMMENT 'Mã đơn thanh toán gốc',
    `user_id`                        bigint(20)   NOT NULL COMMENT 'ID độc giả',
    `refund_amount_vnd`              int          NOT NULL COMMENT 'Số tiền VND hoàn',
    `refund_xu`                      bigint(20)   NOT NULL COMMENT 'Số Xu hoàn/trừ ví',
    `type`                           varchar(20)  NOT NULL COMMENT 'REFUND hoặc CHARGEBACK',
    `status`                         varchar(20)  NOT NULL DEFAULT 'REQUESTED' COMMENT 'REQUESTED, APPROVED, REVERSED, REJECTED',
    `reason`                         varchar(500) DEFAULT NULL,
    `original_ledger_transaction_id` bigint(20)  DEFAULT NULL,
    `reversal_ledger_transaction_id` bigint(20)  DEFAULT NULL,
    `idempotency_key`                varchar(128) NOT NULL,
    `processed_by`                   bigint(20)  DEFAULT NULL,
    `processed_at`                   datetime(3)  DEFAULT NULL,
    `create_time`                    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`                    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    UNIQUE KEY `uk_refund_idempotency` (`idempotency_key`),
    KEY `idx_refund_order` (`out_trade_no`),
    KEY `idx_refund_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Yêu cầu hoàn tiền và chargeback';

-- 2. Bảng quản lý Lô đối soát ngân hàng
CREATE TABLE IF NOT EXISTS `bank_reconciliation_batch` (
    `id`                      bigint(20)  NOT NULL AUTO_INCREMENT,
    `batch_no`                varchar(64) NOT NULL COMMENT 'Mã lô đối soát',
    `pay_channel`             tinyint     NOT NULL COMMENT 'Mã kênh thanh toán',
    `reconcile_date`          date        NOT NULL COMMENT 'Ngày đối soát',
    `total_transactions`      int         NOT NULL DEFAULT 0,
    `matched_transactions`    int         NOT NULL DEFAULT 0,
    `mismatched_transactions` int         NOT NULL DEFAULT 0,
    `total_amount_vnd`        bigint(20)  NOT NULL DEFAULT 0,
    `status`                  varchar(20) NOT NULL DEFAULT 'COMPLETED',
    `create_time`             datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reconcile_batch_no` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Lô đối soát ngân hàng';

-- 3. Bảng chi tiết giao dịch đối soát
CREATE TABLE IF NOT EXISTS `bank_reconciliation_item` (
    `id`                 bigint(20)   NOT NULL AUTO_INCREMENT,
    `batch_id`           bigint(20)   NOT NULL COMMENT 'ID lô đối soát',
    `out_trade_no`       bigint(20)   DEFAULT NULL COMMENT 'Mã đơn hệ thống',
    `bank_trade_no`      varchar(128) DEFAULT NULL COMMENT 'Mã giao dịch phía ngân hàng',
    `amount_vnd`         int          NOT NULL COMMENT 'Số tiền trên sao kê',
    `match_status`       varchar(30)  NOT NULL COMMENT 'MATCHED, AMOUNT_MISMATCH, NOT_FOUND_IN_SYSTEM, NOT_FOUND_IN_BANK',
    `discrepancy_reason` varchar(255) DEFAULT NULL,
    `create_time`        datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_reconcile_item_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Chi tiết đối soát giao dịch ngân hàng';

-- 4. Bảng cấu hình kênh thanh toán
CREATE TABLE IF NOT EXISTS `payment_channel_config` (
    `id`           bigint(20)  NOT NULL AUTO_INCREMENT,
    `channel_code` tinyint     NOT NULL COMMENT '4: VNPAY, 5: VIETQR, 6: NAPAS_247',
    `channel_name` varchar(50) NOT NULL,
    `is_enabled`   tinyint(1)  NOT NULL DEFAULT 1,
    `config_json`  text        DEFAULT NULL COMMENT 'Cấu hình chi tiết mã hóa/JSON',
    `create_time`  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_channel_code` (`channel_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Cấu hình kênh thanh toán';
```

---

## 4. Các API Endpoints Cần Xây dựng / Cập nhật

| Phân hệ | Endpoint | Method | Mục đích |
|---|---|---|---|
| **R1 - Reader** | `/user/refund/request` | `POST` | Độc giả gửi yêu cầu hoàn tiền đơn nạp |
| **R1 - Admin** | `/novel/finance/ledger/transactions` | `GET` | Xem danh sách sổ cái kép `ledger_transaction` |
| **R1 - Admin** | `/novel/finance/ledger/audit` | `GET` | Chạy kiểm tra cân bằng sổ cái kép & projection |
| **R1 - Admin** | `/novel/finance/refunds/list` | `GET` | Danh sách yêu cầu refund / chargeback |
| **R1 - Admin** | `/novel/finance/refunds/{id}/approve` | `POST` | Duyệt refund & thực hiện đảo sổ cái |
| **R1 - Admin** | `/novel/finance/refunds/{id}/reject` | `POST` | Từ chối refund |
| **R1 - Admin** | `/novel/finance/chargeback/record` | `POST` | Ghi nhận giao dịch Chargeback từ ngân hàng |
| **R1 - Admin** | `/novel/finance/reconciliation/upload` | `POST` | Tải file sao kê ngân hàng & thực hiện đối soát |
| **R1 - Admin** | `/novel/finance/reconciliation/batches` | `GET` | Xem lịch sử các lô đối soát ngân hàng |
| **R2 - Front** | `/pay/channels` | `GET` | Lấy danh sách cổng thanh toán khả dụng (VNPAY, VietQR) |
| **R2 - Front** | `/pay/vietqr` | `POST` | Tạo đơn nạp tiền qua VietQR, trả về ảnh QR & chuỗi EMVCo |
| **R2 - Front** | `/pay/vietqr/webhook` | `POST` | Callback/Webhook nhận biến động số dư VietQR |
| **R2 - Front** | `/pay/status/{outTradeNo}` | `GET` | Frontend query kiểm tra trạng thái thanh toán VietQR |
| **R1/R2 - Admin** | `/novel/authorFinance/withdrawals/{id}/auto-payout` | `POST` | Tự động chuyển khoản tác giả qua NAPAS 247 |

---

## 5. Danh mục Unit Test & Integration Test Cần thiết

Để đảm bảo chất lượng và tỷ lệ bao phủ 100%:

1. **Test cho R1 (Sổ cái & Quy trình Tài chính)**:
   - `WalletLedgerServiceImplTest`: 
     - Kiểm thử ràng buộc cân bằng `SUM(amount) == 0`.
     - Kiểm thử chống trùng lặp Idempotency với SHA-256 hash.
     - Kiểm thử giao dịch đảo `reverseTransaction` cho các loại `TOP_UP`, `CHAPTER_PURCHASE`, `AUTHOR_WITHDRAWAL_HOLD`.
     - Kiểm thử khóa đồng thời (Concurrent Locking).
   - `RefundServiceTest`:
     - Kiểm thử tạo yêu cầu hoàn tiền, kiểm tra số dư ví độc giả, duyệt hoàn tiền và đảo sổ cái.
   - `BankReconciliationServiceTest`:
     - Kiểm thử đọc file sao kê, so khớp chính xác các trạng thái `MATCHED`, `AMOUNT_MISMATCH`, `NOT_FOUND_IN_SYSTEM`, `NOT_FOUND_IN_BANK`.
   - `AuthorWithdrawalLifecycleTest`:
     - Verification khép kín luồng rút tiền tác giả từ `PENDING_REVIEW` đến `PAID` và trường hợp hoàn `RELEASE_PENDING`.

2. **Test cho R2 (Payment Adapter & VietQR/NAPAS)**:
   - `PaymentAdapterFactoryTest`: Kiểm thử định tuyến mã kênh sang đúng Adapter (`VNPAY`, `VietQR`).
   - `VietQrGeneratorUtilTest`: Kiểm thử sinh chuỗi EMVCo QR code, tính checksum CRC16 CCITT-FALSE chính xác.
   - `VietQrPaymentAdapterTest`:
     - Kiểm thử khởi tạo đơn VietQR.
     - Kiểm thử xác thực chữ ký Webhook VietQR hợp lệ / không hợp lệ.
     - Kiểm thử Idempotency Webhook gọi lại nhiều lần.
   - `NapasPayoutAdapterTest`: Kiểm thử gọi API NAPAS 247 chi trả thu nhập tác giả.

---

## 6. Kết luận & Khuyến nghị Tiến độ

- **R1 (Payout & Ledger)**: Nền tảng sổ cái kép và rút tiền tác giả cơ bản tốt, nhưng **thiếu hoàn toàn luồng Refund/Chargeback và Đối soát Ngân hàng**. Cần ưu tiên tạo migration `20260720_refund_reconciliation_vietqr.sql`, viết `RefundService` và `BankReconciliationService`.
- **R2 (Payment Adapter & VietQR/NAPAS)**: **Chưa có mã nguồn VietQR/NAPAS**. Cần triển khai ngay interface `PaymentAdapter`, `VietQrGeneratorUtil` sinh chuỗi EMVCo, và `VietQrPaymentAdapter` xử lý Webhook.
