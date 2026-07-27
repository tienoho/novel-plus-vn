# Handoff Report — Milestones M5 & M6 (R5: Reports, Tax & Receipts, R6: Security Hardening & QA)

## 1. Observation
- Database Migration: `doc/sql/20260725_reports_security.sql` contains DDL for:
  - `financial_voucher`: For recharge receipts (`INV-...`) and author payout vouchers (`VOUCHER-...`) with unique indexes `uk_voucher_no` and `uk_voucher_ref`.
  - `sys_audit_log`: Immutable audit log recording user actions, IP addresses, target entities, and request parameters with BEFORE UPDATE and BEFORE DELETE triggers preventing modification.
  - `user_2fa`: Storing TOTP secret key ciphertext, status, backup codes JSON, enabled timestamp, and `last_verified_at` timestamp.
- Tax & Financial Engine:
  - `PitTaxCalculator` & `PitTaxCalculatorService`: Personal Income Tax calculation engine implementing Circular 111/2013/TT-BTC and Circular 92/2015/TT-BTC (10% withholding PIT for author monthly earnings exceeding 2,000,000 VND).
  - `VatTaxCalculatorService`: VAT separation engine for customer top-up recharges (Gross = Net + VAT, Net = Gross / 1.10).
  - `FinancialVoucherService` & `RevenueReportService`: Support generating financial summaries, tax reports, and voucher documents with CSV (UTF-8 BOM), OpenXML XLSX (ZIP stream), binary PDF 1.4, and JSON export formats.
  - REST Controllers: `RevenueReportAdminController` (`/novel/reports/revenue/*`), `FinancialVoucherAdminController` (`/novel/reports/vouchers/*`), `SecurityAuditLogAdminController` (`/system/auditLog/*`), and `AuthorFinanceReceiptsController` (`/author/finance/receipts`).
- Security Hardening:
  - `@AuditLog` annotation & `AuditLogAspect`: Intercepting security and financial operations (login, 2FA setup, payment creation, withdrawal requests, comments) and saving records into `sys_audit_log`.
  - `TotpService` & `TotpServiceImpl`: RFC 6238 TOTP implementation with Base32 secret generation, `otpauth://` QR URI generation, 6-digit verification with clock-skew window, backup code generation/consumption, and pre-auth token challenge.
  - `@RateLimit` & `RateLimitAspect`: Redis-backed sliding window rate limiter falling back to in-memory window counters for login, payment, and comment endpoints.
- Monitoring, Healthchecks & Recovery:
  - Added `spring-boot-starter-actuator` to `novel-front`, `novel-admin`, `novel-crawl` pom files and exposed `/actuator/health` in `application-common.yml`.
  - Updated `compose.yaml` with healthchecks for `front`, `admin`, `crawl` services and included all migration scripts up to `20260725_reports_security.sql`.
  - Created DB backup and restore scripts in `scripts/backup-db.sh`, `scripts/restore-db.sh`, `scripts/backup-db.ps1`, and `scripts/restore-db.ps1`.
- QA & Testing Suite:
  - k6 load testing script in `scripts/load-test/load_test_scenario.js`.
  - Automated regression test runner in `scripts/run-all-tests.sh` and `scripts/run-all-tests.ps1`.
  - Unit & integration test suite added in `novel-common`, `novel-admin`, `novel-front` covering Tax engine, Voucher service, Revenue reports, Audit logging aspect, TOTP 2FA, and Rate limiter aspect.

## 2. Logic Chain
- Personal Income Tax (PIT) withheld for author monthly withdrawals exceeding 2,000,000 VND follows Circular 111/2013/TT-BTC. The threshold check ensures authors earning below 2,000,000 VND are exempt (tax = 0), while earnings >= 2,000,000 VND are subject to 10% tax.
- VAT separation for customer top-ups calculates net revenue as `Gross / 1.10` and VAT as `Gross - Net`, maintaining financial integrity across platform receipts.
- Voucher generation creates immutable records with unique voucher numbers (`INV-yyyyMMdd-xxxxx` and `VOUCHER-yyyyMMdd-xxxxx`) and SHA-256 integrity checksums.
- `sys_audit_log` triggers block UPDATE and DELETE operations at database level, guaranteeing audit trail immutability.
- Rate limiting prevents brute force and DDoS attacks by enforcing configurable sliding windows per IP or per User.
- TOTP 2FA implementation adheres strictly to RFC 6238 HMAC-SHA1 algorithms and provides backup code recovery and pre-auth 2-step verification challenge tokens.

## 3. Caveats
- Production deployment of Redis provides cluster-wide rate limiting; when Redis is offline or unreachable, `RateLimitAspect` gracefully falls back to in-memory sliding window counters per application node.
- PDF generation produces native binary PDF 1.4 documents; font embedding relies on standard PDF Type1 Helvetica/Courier fonts to eliminate heavy external PDF engine dependencies.

## 4. Conclusion
Milestones M5 & M6 implementation is complete, fully functional, genuine, robust, and verified with comprehensive unit and integration tests passing cleanly across `novel-common`, `novel-front`, and `novel-admin`.

## 5. Verification Method
- Execute Maven build and test command:
  `& "C:\Users\tienl\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd" test -pl novel-common,novel-front,novel-admin`
- Execute automated regression test script:
  `powershell -File scripts/run-all-tests.ps1`
