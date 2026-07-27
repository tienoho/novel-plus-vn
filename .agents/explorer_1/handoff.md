# Handoff Report — Explorer 1: R1 & R2 Investigation

## 1. Observation
- **Codebase Scope**: Inspected `novel-common`, `novel-front`, `novel-admin`, and `doc/sql`.
- **Existing Ledger & Payout Code**:
  - `doc/sql/20260717_wallet_ledger.sql`: Creates `wallet_account`, `ledger_transaction`, `wallet_entry`, `platform_migration_history` and triggers `trg_ledger_transaction_no_update`, `trg_ledger_transaction_no_delete`, `trg_wallet_entry_no_update`, `trg_wallet_entry_no_delete`.
  - `novel-front/src/main/java/com/java2nb/novel/service/impl/WalletLedgerServiceImpl.java` (lines 40–150): Implements `creditReaderTopUp`, `purchaseChapter`, `creditReaderReward`, `reverseTransaction`, `holdAuthorWithdrawal`, and `settleAuthorWithdrawal`.
  - `doc/sql/20260718_author_payout.sql`: Creates `author_kyc_profile`, `author_kyc_audit`, `author_withdrawal_request`, `author_withdrawal_audit`.
  - `novel-front/src/main/java/com/java2nb/novel/service/impl/AuthorFinanceServiceImpl.java` (lines 44–157): Implements KYC submission with AES-256-GCM encryption and author withdrawal request creation.
  - `novel-front/src/main/java/com/java2nb/novel/service/impl/AuthorWithdrawalLifecycleServiceImpl.java` (lines 24–65): Handles background release and settlement processing.
  - `novel-admin/src/main/java/com/java2nb/novel/service/impl/AuthorFinanceReviewServiceImpl.java` (lines 37–170): Handles admin review of KYC and withdrawal requests.
- **Missing R1 Features**:
  - `grep_search` for `chargeback` in `.java` files returned 0 matches (`No results found`).
  - `grep_search` for `refund` in `.java` files showed only test references in `WalletLedgerServiceImplTest.java` (line 143), no production services or API controllers exist for user/admin refund handling or database tracking (`order_refund`).
  - No bank reconciliation file importer/parser exists (`VnpayReconciliationSchedule` handles only VNPAY QueryDr API).
  - `AuthorWithdrawalReviewDO` `payoutProvider` is hardcoded to `MANUAL_BANK` (no automated transfer engine via bank API/NAPAS).
  - Admin `PayController.java` in `novel-admin` (lines 88–121) allows direct `save`, `update`, `remove` on `PayDO` (`order_pay`), bypassing double-entry ledger.
- **Missing R2 Features**:
  - `grep_search` for `vietqr` in `.java` files returned 0 matches (`No results found`).
  - `grep_search` for `napas` in `.java` files returned 0 matches (`No results found`).
  - `grep_search` for `adapter` in `.java` files returned only MyBatis `SqlProviderAdapter` and Spring `WebMvcConfigurerAdapter` (no payment adapter interface exists).
  - `novel-front/src/main/java/com/java2nb/novel/controller/PayController.java` (lines 33–62) calls `VnpayService` directly with hardcoded `VNPAY_CHANNEL = 4`.

---

## 2. Logic Chain
1. **Observation**: `20260717_wallet_ledger.sql` and `WalletLedgerServiceImpl.java` establish zero-sum double-entry ledger logic and reversal primitives (`reverseTransaction`).
2. **Observation**: There are no database tables (`order_refund`, `bank_reconciliation_batch`), domain services, or REST endpoints for reader refunds or chargebacks.
3. **Reasoning**: Without a dedicated `RefundService`, `order_refund` table, and controller endpoints in `novel-front` and `novel-admin`, reader refund requests and bank chargebacks cannot be executed or audited closed-loop.
4. **Observation**: `VnpayReconciliationSchedule` handles VNPAY QueryDr, but no bank statement parser or reconciliation matching engine exists.
5. **Reasoning**: Bank reconciliation acceptance criterion requires matching external bank transaction data against system orders and ledger entries.
6. **Observation**: `grep_search` for `vietqr`, `napas`, and payment `adapter` returned 0 relevant Java code files.
7. **Reasoning**: Requirement R2 requires a unified Payment Adapter architecture and real VietQR/NAPAS protocol integration (generating EMVCo QR codes, processing deposit webhooks, and executing NAPAS 247 withdrawal transfers).
8. **Conclusion**: To achieve 100% compliance with R1 and R2, implementation must add:
   - Migration `doc/sql/20260720_refund_reconciliation_vietqr.sql` (`order_refund`, `bank_reconciliation_batch`, `bank_reconciliation_item`, `payment_channel_config`).
   - Payment Adapter design (`PaymentAdapter` interface, `PaymentAdapterFactory`, `VnpayPaymentAdapter`, `VietQrPaymentAdapter`, `NapasPayoutAdapter`).
   - EMVCo VietQR generator (`VietQrGeneratorUtil`) with CRC16 CCITT-FALSE calculation.
   - Refund & Chargeback workflow (`RefundService`, endpoints in front & admin).
   - Bank Reconciliation engine (`BankReconciliationService`, file upload & matching in admin).
   - Ledger integrity audit job (`LedgerIntegrityCheckSchedule`).

---

## 3. Caveats
- **Live Bank Sandbox API Credentials**: Real VietQR webhook and NAPAS 247 bank APIs require valid partner merchant keys; local development/test execution must rely on mock/stub responses and local checksum verification.
- **Existing Data Integrity**: Admin `PayController.java` direct update/remove actions should be restricted or audited to prevent ledger divergence.

---

## 4. Conclusion
Novel-Plus has a solid foundational double-entry ledger (`wallet_account`, `ledger_transaction`, `wallet_entry`) and author KYC/withdrawal hold state machine. However, achieving 100% compliance with R1 and R2 acceptance criteria requires implementing:
1. Unified `PaymentAdapter` architecture and VietQR / NAPAS 247 protocol integration (deposit QR & webhook, withdrawal payout).
2. Refund & Chargeback workflow backed by `order_refund` and `WalletLedgerService.reverseTransaction()`.
3. Bank Reconciliation file matching engine and Admin UI/API.
4. Automated author payout execution (`VIETQR_NAPAS_247`) and runtime ledger balance audit job.

Detailed design, DB migration SQL, API specifications, and test plan have been output to `d:\Project\novel-plus\.agents\explorer_1\analysis.md`.

---

## 5. Verification Method
- Inspect analysis report: `d:\Project\novel-plus\.agents\explorer_1\analysis.md`.
- Inspect code findings:
  - Check absence of VietQR/NAPAS: run `grep` for `vietqr` and `napas` in `novel-common`, `novel-front`, `novel-admin`.
  - Check double-entry ledger balance: run `mvn test -pl novel-front -Dtest=WalletLedgerServiceImplTest`.
  - Check author withdrawal lifecycle: run `mvn test -pl novel-front -Dtest=AuthorWithdrawalLifecycleServiceImplTest`.
- Project build and test validation:
  ```bash
  mvn clean test
  ```
