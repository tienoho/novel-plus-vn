## 2026-07-25T11:05:00Z
You are Worker 3 implementing Milestones M5 (R5: Reports, Tax & Receipts) and M6 (R6: Security, Hardening & QA).
Your working directory is d:\Project\novel-plus\.agents\worker_m5_m6.

Tasks:
1. Create DB migration file `doc/sql/20260725_reports_security.sql` containing DDL for `financial_voucher`, `sys_audit_log`, `user_2fa`, and audit triggers.
2. Implement automated PIT calculation engine (10% rate for earnings > 2,000,000 VND per Circular 111/2013/TT-BTC) and VAT calculation engine in `novel-common`/`novel-front`.
3. Implement `FinancialVoucherService` issuing customer top-up receipts (`INV-...`) and author payout vouchers (`VOUCHER-...`) with PDF/CSV export support.
4. Implement `RevenueReportService` supporting multi-period revenue reporting breakdowns (Daily/Monthly/Quarterly/Annual) with CSV, XLSX, and PDF exports in `novel-admin`.
5. Implement immutable audit logging system (`sys_audit_log` table, `@AuditLog` annotation, `AuditLogAspect`) across `novel-front` and `novel-admin`.
6. Implement 2FA TOTP engine (RFC 6238 with QR code generation & pre-auth login step) in `user_2fa` & `TotpService`.
7. Implement Redis sliding window `@RateLimit` aspect for API protection.
8. Add Spring Actuator endpoints (`spring-boot-starter-actuator`), Docker Compose container healthchecks in `compose.yaml`, automated DB backup/restore scripts (`scripts/backup-db.sh`, `scripts/restore-db.sh`).
9. Build k6 load testing scripts in `scripts/load-test/` and automated regression test runner (`scripts/run-all-tests.sh`).
10. Add unit and integration tests for PIT/VAT calculation, voucher generation, revenue reporting, audit logging, TOTP 2FA, rate limiting, and backup scripts.
11. Run `mvn clean test` using run_command to verify everything compiles and passes 100%.
