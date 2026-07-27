## 2026-07-25T05:20:43Z
You are Replacement Worker 3 assigned to Milestones M5 & M6 (R5: Reports, Tax & Receipts, R6: Security Hardening & QA) for Novel-Plus.
Your working directory is d:\Project\novel-plus\.agents\worker_m5_m6_2.

Please create BRIEFING.md and progress.md in your working directory and execute the following tasks:

1. Database Migration:
   Create `doc/sql/20260725_reports_security.sql` with DDL statements for:
   - `financial_voucher`: For revenue receipts (`INV-...`) and author withdrawal payout vouchers (`VOUCHER-...`).
   - `sys_audit_log`: Immutable audit log table recording user actions, IP addresses, target entities, and changes.
   - `user_2fa`: Table storing TOTP secrets, status, recovery codes, and last verification timestamp.

2. Tax & Financial Engine (`novel-common`, `novel-front`, `novel-admin`):
   - `PitTaxCalculator`: Personal Income Tax calculation engine (10% rate for author monthly withdrawal earnings exceeding 2,000,000 VND per Circular 111/2013/TT-BTC) and VAT separation engine.
   - `RevenueReportService` & `FinancialVoucherService`: Generating financial summaries, tax reports, and voucher documents with CSV, XLSX, PDF, and JSON export support.
   - REST Controllers in `novel-admin` (`/novel/reports/*`) and `novel-front` (`/author/finance/receipts`).

3. Security & Hardening (`novel-common`, `novel-front`, `novel-admin`):
   - Audit Logging: `@AuditLog` annotation and `AuditLogAspect` covering critical security and financial methods in both `novel-front` and `novel-admin`, writing to `sys_audit_log`.
   - 2FA TOTP Engine: `TotpService` (RFC 6238 implementation with QR code URI rendering and 2-step verification challenge).
   - Rate Limiting: Redis-backed `@RateLimit` aspect supporting sliding window rate limiting on login, payment, and comment endpoints.

4. Monitoring, Healthcheck & Recovery Scripts:
   - Enable Spring Boot Actuator (`spring-boot-starter-actuator`) dependencies & endpoint configs.
   - Update `compose.yaml`: add healthchecks for `front`, `admin`, and `crawl` services.
   - Add DB backup & restore scripts in `scripts/backup-db.sh` and `scripts/restore-db.sh` (or .ps1 equivalent for Windows).

5. QA & Testing Suite:
   - Add k6 load testing scripts in `scripts/load-test/load_test_scenario.js`.
   - Add automated regression test runner in `scripts/run-all-tests.sh` (or .ps1).
   - Comprehensive unit & integration tests covering Tax engine, Voucher service, Revenue report exports, Audit log aspect, TOTP 2FA, and Rate limiter aspect.

6. Verification:
   - Run Maven build & test command:
     `& "C:\Users\tienl\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd" test -pl novel-common,novel-front,novel-admin`
   - Produce a detailed handoff.md in your working directory `d:\Project\novel-plus\.agents\worker_m5_m6_2\handoff.md`.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.
