## 2026-07-25T04:04:40Z
You are Worker 1 implementing Milestones M1 (R1: Payout, Ledger & Financial Workflow) and M2 (R2: Payment Adapter & VietQR/NAPAS Integration).
Your working directory is d:\Project\novel-plus\.agents\worker_m1_m2 (create it if needed).

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Read instructions & technical design in:
- d:\Project\novel-plus\.agents\explorer_1\analysis.md
- d:\Project\novel-plus\.agents\explorer_1\handoff.md
- d:\Project\novel-plus\.agents\ORIGINAL_REQUEST.md

Tasks:
1. Create DB migration file `doc/sql/20260720_refund_reconciliation_vietqr.sql` containing DDL for `order_refund`, `bank_reconciliation_batch`, `bank_reconciliation_item`, `payment_channel_config`.
2. Implement double-entry refund & chargeback workflow in `novel-front` and `novel-admin` (`RefundService`, calling `WalletLedgerService.reverseTransaction`, creating `order_refund` records, REST endpoints).
3. Implement Bank Reconciliation matching engine (`BankReconciliationService`, processing bank statement files, updating batch & item status, admin controller endpoints).
4. Implement automated author payout execution via `VIETQR_NAPAS_247` in `AuthorWithdrawalLifecycleServiceImpl` and `AuthorFinanceReviewServiceImpl`.
5. Implement runtime ledger balance audit job (`LedgerIntegrityCheckSchedule`) running periodic zero-sum ledger integrity checks.
6. Implement `PaymentAdapter` unified architecture (`PaymentAdapter` interface, `PaymentAdapterFactory`, `VnpayPaymentAdapter`, `VietQrPaymentAdapter`, `NapasPayoutAdapter`).
7. Implement EMVCo VietQR string generator utility (`VietQrGeneratorUtil`) with CRC16 CCITT-FALSE computation.
8. Implement VietQR deposit webhook handler (`/pay/vietqr/webhook`) with signature/checksum validation, idempotency, and ledger crediting via `WalletLedgerService.creditReaderTopUp`.
9. Add comprehensive unit and integration tests for all newly introduced services, controllers, adapters, and utilities.
10. Run `mvn clean test` (or `mvn clean test -pl novel-common,novel-front,novel-admin`) using run_command to verify everything compiles and passes 100%.

Deliver report in d:\Project\novel-plus\.agents\worker_m1_m2\handoff.md detailing implemented files, build output, test results, and verification commands. Send a message to parent when done.
