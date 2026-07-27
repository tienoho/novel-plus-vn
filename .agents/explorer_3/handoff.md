# Handoff Report: Requirement R5 (Reports, Tax & Receipts) and R6 (Security, Hardening & Quality Assurance)

**Agent**: Explorer 3  
**Working Directory**: `d:\Project\novel-plus\.agents\explorer_3`  
**Date**: 2026-07-25  

---

## 1. Observation

Direct observations from examining codebase files, SQL schemas, and build configurations:

1. **Revenue Reporting & Statistics**:
   - `d:\Project\novel-plus\novel-admin\src\main\java\com\java2nb\novel\controller\StatController.java`:
     - Line 47–58 (`countUser`): Performs count queries on users, authors, books, and order pay.
     - Line 61–77 (`tableSta`): Returns date-based counts for the past 7 days (`userService.tableSta`, `bookService.tableSta`, `authorService.tableSta`, `orderPayService.tableSta`).
     - Observation: Only count statistics exist. No revenue summary, no platform vs author earnings breakdown, no custom date filtering, and no export endpoints (CSV/XLSX/PDF).
2. **Tax Calculation (PIT / VAT)**:
   - `d:\Project\novel-plus\novel-admin\src\main\java\com\java2nb\novel\controller\AuthorFinanceController.java`:
     - Line 91–95:
       ```java
       public R approveWithdrawal(@PathVariable long id, @RequestParam long expectedVersion,
                                  @RequestParam(defaultValue = "0") long withheldTaxVnd) {
           service.approveWithdrawal(id, expectedVersion, withheldTaxVnd, getUserId());
           return R.ok();
       }
       ```
     - Line 114 in `AuthorFinanceReviewServiceImpl.java`: `withheldTaxVnd` is validated only for non-negativity and `<= grossAmountVnd`.
     - Observation: Tax withholding is passed manually as an HTTP parameter by the admin user during approval. No automated PIT calculation engine exists (e.g. 10% PIT on earnings > 2,000,000 VND per Circular 111/2013/TT-BTC & Circular 92/2015/TT-BTC), and no VAT calculation on VNPAY top-ups exists.
3. **Financial Receipts & Invoice Vouchers**:
   - Grep search across `d:\Project\novel-plus` for `receipt`, `voucher`, `invoice` returned zero domain models, database tables, or controller endpoints for issuing or rendering vouchers.
   - Observation: No financial receipt or voucher generation engine exists.
4. **Audit Logging (R6)**:
   - `d:\Project\novel-plus\novel-admin\src\main\java\com\java2nb\common\aspect\LogAspect.java`:
     - Line 45: `@Pointcut("@annotation(com.java2nb.common.annotation.Log)")` logs method executions in `novel-admin` to `sys_log`.
     - `novel-front`: File search for `*Log*.java` in `novel-front` returned 0 results.
     - Observation: Audit logging is restricted to `novel-admin` method annotations and absent from `novel-front`. Security events (failed logins, 2FA attempts, PII access, rate limit trips) are not standardly audited into an immutable table.
5. **Two-Factor Authentication (2FA / TOTP) (R6)**:
   - Grep search across `d:\Project\novel-plus` for `2fa`, `totp`, `twofactor` returned 0 code occurrences.
   - Observation: 2FA is completely missing.
6. **Rate Limiting (R6)**:
   - Grep search across `d:\Project\novel-plus` for `rateLimiter`, `RateLimit`, `bucket` returned 0 rate limiter implementations.
   - Observation: Endpoint rate limiting is missing.
7. **Health Monitoring & Backup/Recovery (R6)**:
   - `d:\Project\novel-plus\pom.xml`, `novel-common/pom.xml`, `novel-front/pom.xml`, `novel-admin/pom.xml`: `spring-boot-starter-actuator` is omitted.
   - `d:\Project\novel-plus\compose.yaml`: Services `front`, `admin`, `crawl` lack `healthcheck` blocks (only `mysql` and `redis` have healthchecks).
   - `d:\Project\novel-plus\doc\deployment.md` line 61–65: Manual `mysqldump` command documented, but no automated backup/restore scripts or verification tools exist.
8. **Test Suite & QA (R6)**:
   - `d:\Project\novel-plus\scripts`: `find_by_name` returned only `verify-i18n.mjs`.
   - File search `*Test*.java`: 18 test files exist (covering i18n, VNPAY, wallet ledger, author withdrawal unit logic), but no load testing scripts (k6/JMeter) or automated regression runner scripts exist.

---

## 2. Logic Chain

1. **R5 Compliance Gap**:
   - Observation 1 shows `StatController` only counts rows for dashboard display.
   - Observation 2 shows `AuthorFinanceController` requires admin to manually type `withheldTaxVnd`.
   - Observation 3 shows no receipt/voucher models or DDL exist.
   - Logical Step: Without automated PIT/VAT calculation, revenue reporting breakdown, CSV/XLSX/PDF export utilities, and voucher generation/issuance, Requirement R5 acceptance criteria ("Xuất báo cáo thuế và chứng từ doanh thu đúng định dạng") cannot pass.

2. **R6 Compliance Gap**:
   - Observation 4 shows `novel-front` lacks audit logging and security events are not centrally tracked in an immutable table.
   - Observation 5 shows 2FA TOTP authentication is missing.
   - Observation 6 shows rate limiting is missing on authentication, payment, and comment endpoints.
   - Observation 7 shows Actuator monitoring is not packaged and Docker Compose application healthchecks are missing.
   - Observation 8 shows load testing and automated regression test runner scripts are missing.
   - Logical Step: Requirement R6 acceptance criteria ("Đạt yêu cầu test tải lớn, bảo mật 2FA/rate limit và khôi phục sự cố monitoring/backup") requires building the missing security controls, monitoring endpoints, backup scripts, and k6 load test scripts.

---

## 3. Caveats

- **External Network Restrictions**: The investigation operated under `CODE_ONLY` network mode; all analysis is derived purely from local source code, configuration files, and SQL migration scripts.
- **Third-Party Tax & E-Invoice Integration**: Automated tax calculation is designed around standard Vietnamese Personal Income Tax (PIT) withholding rules (Circular 111/2013/TT-BTC) and Value Added Tax (VAT) principles. Integration with third-party electronic invoice providers (e.g. VNPT/Viettel E-Invoice API) is out of scope for local voucher PDF/CSV generation.

---

## 4. Conclusion

To achieve 100% compliance with P0 acceptance criteria for R5 and R6:
1. **R5 Technical Deliverables Needed**:
   - Database table `financial_voucher` for customer top-up receipts (`INV-...`) and author payout vouchers (`VOUCHER-...`).
   - Automated PIT calculation engine (10% rate above 2,000,000 VND threshold) & VAT separation engine.
   - `RevenueReportService` and `FinancialVoucherService` supporting CSV, XLSX, PDF, and JSON exports.
   - REST controllers in `novel-admin` (`/novel/reports/*`) and `novel-front` (`/author/finance/receipts`).
2. **R6 Technical Deliverables Needed**:
   - Database table `sys_audit_log` with immutable SQL triggers, `@AuditLog` annotation, and `AuditLogAspect` covering `novel-front` and `novel-admin`.
   - Database table `user_2fa` and `TotpService` (RFC 6238) for 2FA enrollment, QR code rendering, and 2-step login verification.
   - `@RateLimit` annotation and Redis-backed sliding window / token bucket `RateLimitAspect`.
   - Actuator integration (`spring-boot-starter-actuator`), Docker Compose container healthchecks, and automated DB backup/restore scripts (`scripts/backup-db.sh`, `scripts/restore-db.sh`).
   - QA load testing suite in `scripts/load-test/` (k6 scripts for checkout, reading, payout) and automated regression test runner (`scripts/run-all-tests.sh`).

---

## 5. Verification Method

Independent verification of findings and future implementation compliance:

1. **Inspect Analysis Report**:
   - Read `d:\Project\novel-plus\.agents\explorer_3\analysis.md` for full DDL definitions, API specifications, and task breakdown.
2. **Verify Missing R5 Code**:
   - Execute grep search: `grep_search(Query="financial_voucher", SearchPath="d:/Project/novel-plus")` -> Expected 0 matches prior to implementation.
   - Execute grep search: `grep_search(Query="PitTaxCalculator", SearchPath="d:/Project/novel-plus")` -> Expected 0 matches prior to implementation.
3. **Verify Missing R6 Code**:
   - Execute grep search: `grep_search(Query="RateLimit", SearchPath="d:/Project/novel-plus")` -> Expected 0 matches prior to implementation.
   - Execute grep search: `grep_search(Query="TotpService", SearchPath="d:/Project/novel-plus")` -> Expected 0 matches prior to implementation.
   - Execute grep search: `grep_search(Query="sys_audit_log", SearchPath="d:/Project/novel-plus")` -> Expected 0 matches prior to implementation.
4. **Post-Implementation Build & Test Commands**:
   ```bash
   mvn clean test
   node scripts/verify-i18n.mjs
   docker compose config --quiet
   ```
