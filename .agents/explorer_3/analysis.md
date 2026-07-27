# Detailed Technical Analysis: Requirements R5 & R6

**Author**: Explorer 3  
**Target Requirements**: R5 (Reports, Tax & Receipts) & R6 (Security, Hardening & Quality Assurance)  
**Date**: 2026-07-25  
**Project**: Novel-Plus (`novel-common`, `novel-front`, `novel-admin`, `novel-crawl`, `doc/sql`, `config`)

---

## 1. Executive Summary & Baseline Assessment

A comprehensive audit of the Novel-Plus codebase was conducted to evaluate readiness for **R5 (Reports, Tax & Receipts)** and **R6 (Security, Hardening & Quality Assurance)**. 

### Key Findings
1. **R5 (Reports, Tax & Receipts)**:
   - **Current State**: Only basic count statistics exist in `StatController.java` (lines 47–77 in `novel-admin`). `AuthorFinanceController.java` (line 92) accepts a manual `withheldTaxVnd` request parameter during payout approval, but there is **no automated tax calculation engine** (PIT/VAT), no revenue reporting breakdown system, no export services (CSV/XLSX/PDF), and no financial receipt or invoice voucher generation mechanism.
   - **Compliance Gap**: 100% missing for automated tax calculation, revenue reporting, export options, and receipt/voucher issuance.

2. **R6 (Security, Hardening & Quality Assurance)**:
   - **Current State**: 
     - **Audit Logs**: `LogAspect.java` exists only in `novel-admin` for `@Log` annotated methods. `novel-front` has **zero audit logging** for user actions, security events, or financial activities.
     - **2FA**: Zero code for Two-Factor Authentication (TOTP / RFC 6238) exists across all modules.
     - **Rate Limiting**: No rate limiting mechanism (Redis/bucket/sliding window) exists on any API endpoints in `novel-front` or `novel-admin`.
     - **Monitoring**: `spring-boot-starter-actuator` is omitted from all `pom.xml` files. `compose.yaml` lacks container healthchecks for `front`, `admin`, and `crawl`.
     - **Backup/Recovery**: Manual `mysqldump` command documented in `doc/deployment.md`, but no automated backup/restore scripts or verification tools exist.
     - **Test Suite & QA**: Existing tests cover basic i18n, VNPAY, wallet ledger, and author withdrawal unit logic, but there are **no load test scripts** (k6/JMeter), no E2E test suite, and no automated regression runner script.
   - **Compliance Gap**: Critical missing security controls, monitoring endpoints, rate limiters, 2FA, and QA infrastructure.

---

## 2. Requirement R5: Reports, Tax & Receipts — Deep-Dive Investigation

### 2.1 Revenue Reporting System
- **Objective**: Aggregate platform and author financial metrics over customizable time windows (daily, monthly, quarterly, yearly, or custom date ranges) and provide multi-format exports (CSV, XLSX, PDF, JSON).
- **Domain Metrics Required**:
  1. **Platform Revenue**:
     - Gross VNPAY Recharges (`order_pay` where `pay_status = 1`).
     - Net Platform Revenue (Gross Recharges minus VAT & Author payout obligations).
     - Reader Xu Circulation (Total Xu issued vs total Xu consumed on chapter purchases).
     - Platform Commission Share (Platform percentage retained from chapter purchases).
  2. **Author Revenue**:
     - Gross Author Income (accumulated chapter purchase revenue share).
     - Total Payout Requests (`PENDING_REVIEW`, `APPROVED`, `PAID`, `REJECTED`).
     - Total Withheld Personal Income Tax (PIT / Thuế TNCN).
     - Net Author Payouts Disbursed.
- **Reporting Period Aggregation**:
  - `DAILY`: Grouped by `DATE(create_time)`.
  - `MONTHLY`: Grouped by `DATE_FORMAT(create_time, '%Y-%m')`.
  - `QUARTERLY`: Grouped by `YEAR(create_time)` and `QUARTER(create_time)`.
  - `ANNUAL`: Grouped by `YEAR(create_time)`.
- **Export Format Specification**:
  - `JSON`: REST API payload.
  - `CSV`: Standard RFC 4180 CSV output with UTF-8 BOM (`\uFEFF`) for Excel Vietnamese character compatibility.
  - `XLSX` (Excel): Formatted workbook with summary tables and headers.
  - `PDF`: Printable financial statement layout with official header, signature line, and tabular breakdown.

### 2.2 Tax Calculation Engine (PIT & VAT Logic)
- **Vietnamese Tax Compliance Framework**:
  1. **Personal Income Tax (PIT / Thuế TNCN) for Author Payouts**:
     - **Legal Rule** (Circular 111/2013/TT-BTC & Circular 92/2015/TT-BTC): Payments to individuals for royalties/freelance income exceeding 2,000,000 VND per transaction/cycle are subject to a 10% PIT withholding rate (or policy-configured rate).
     - **Automated Calculation Logic**:
       ```java
       public class PitTaxResult {
           private long grossAmountVnd;
           private long taxableAmountVnd;
           private long withheldTaxVnd;
           private long netAmountVnd;
           private double taxRate; // Default 0.10 (10%)
           private long exemptionThresholdVnd; // Default 2,000,000 VND
       }
       ```
       If `grossAmountVnd >= exemptionThresholdVnd` and author is registered for tax withholding, `withheldTaxVnd = Math.round(grossAmountVnd * taxRate)`. Net amount = `grossAmountVnd - withheldTaxVnd`.
     - **Admin Override**: Admin can verify or adjust tax withheld during payout approval, but the engine provides the exact computed default automatically.
  2. **Value Added Tax (VAT / Thuế GTGT) for Platform Revenue**:
     - Digital content services / payment recharges are subject to standard VAT rules (default 10% or 8% depending on tax regime).
     - Formula: `Gross VNPAY Amount = Net Platform Revenue + VAT Amount`.
     - `VAT Amount = Gross VNPAY Amount - Math.round(Gross VNPAY Amount / (1 + vatRate))`.
  3. **Tax Authority Reporting**:
     - Exportable PIT Withholding Statement (Báo cáo tổng hợp thuế TNCN khấu trừ từ thu nhập tác giả).
     - Exportable VAT Revenue Summary (Báo cáo doanh thu và thuế GTGT nạp tiền qua VNPAY).

### 2.3 Financial Receipt & Invoice Voucher Generation
- **Voucher Serial & Numbering Scheme**:
  - Customer Payment Receipt (Chứng từ nạp Xu): `INV-YYYYMMDD-XXXXX` (e.g., `INV-20260725-00001`).
  - Author Payout Voucher (Phiếu chi thanh toán tác giả): `VOUCHER-YYYYMMDD-XXXXX` (e.g., `VOUCHER-20260725-00001`).
- **Receipt / Voucher Lifecycle**:
  - `ISSUED`: Auto-generated upon transaction completion (`order_pay` status = 1 or `author_withdrawal_request` status = `PAID`).
  - `CANCELLED`: Marked cancelled if transaction refunded/reversed.
- **Render & Export Options**:
  - Printable HTML template view in admin and author backend.
  - Downloadable PDF voucher with cryptographic hash / checksum for verification.

---

## 3. Requirement R6: Security, Hardening & Quality Assurance — Deep-Dive Investigation

### 3.1 Audit Logging Architecture
- **Scope**: Must record all security-sensitive operations across both `novel-front` and `novel-admin`.
- **Event Taxonomy**:
  - `AUTH_LOGIN_SUCCESS`, `AUTH_LOGIN_FAILED`, `AUTH_LOGOUT`, `AUTH_2FA_CHALLENGE`
  - `USER_PASSWORD_CHANGE`, `USER_PROFILE_UPDATE`, `USER_SUSPENDED`
  - `PII_VIEWED` (Decrypting/Viewing KYC or Bank Details)
  - `KYC_APPROVED`, `KYC_REJECTED`
  - `PAYOUT_APPROVED`, `PAYOUT_PROCESSING`, `PAYOUT_PAID`, `PAYOUT_REJECTED`
  - `ROLE_MODIFIED`, `PERMISSION_GRANTED`
  - `CONTENT_MODERATED`, `COPYRIGHT_TAKEDOWN`
- **Immutability Protection**:
  - DB Table `sys_audit_log` with MySQL BEFORE UPDATE / BEFORE DELETE triggers throwing SQLSTATE `45000` to prevent tampering.
  - Asynchronous log consumer (`@Async` / Event Publisher) so logging never blocks HTTP response execution.

### 3.2 Two-Factor Authentication (2FA) Implementation
- **Standard**: RFC 6238 Time-Based One-Time Password (TOTP) algorithm (6 digits, 30-second time step, HMAC-SHA1).
- **Enrollment Flow**:
  1. User/Admin requests 2FA setup (`POST /api/2fa/setup`).
  2. Server generates Base32 secret key (160-bit random secret) and QR Code URI (`otpauth://totp/NovelPlus:username?secret=...&issuer=NovelPlus`).
  3. Client scans QR code with Google Authenticator or Authy.
  4. Client submits initial 6-digit TOTP code (`POST /api/2fa/enable`).
  5. Server verifies code, persists secret (AES-256 encrypted), generates 8 emergency backup codes (hashed), and enables 2FA (`is_enabled = 1`).
- **Authentication Flow Integration**:
  - In `novel-front` (JWT auth) and `novel-admin` (Shiro auth):
  - When user submits correct username/password, server checks if 2FA is enabled.
  - If enabled, server returns a temporary pre-auth token (`2FA_REQUIRED_TOKEN`) requiring TOTP verification (`POST /login/2fa`).
  - Upon valid TOTP or backup code match, final JWT session token or Shiro subject session is issued.

### 3.3 Redis Rate Limiting Engine
- **Mechanism**: Distributed Token Bucket / Sliding Window algorithm implemented via Redisson / Redis Lua Script.
- **Annotation-Driven Design**:
  ```java
  @Target(ElementType.METHOD)
  @Retention(RetentionPolicy.RUNTIME)
  public @interface RateLimit {
      String key() default "";
      int count() default 10;
      int timeWindowSeconds() default 60;
      LimitType limitType() default LimitType.IP; // IP, USER, GLOBAL
      String message() default "Yêu cầu quá nhiều, vui lòng thử lại sau.";
  }
  ```
- **Target Endpoints**:
  - Login & 2FA (`/login`, `/login/2fa`): 5 requests / minute / IP.
  - Password Reset & SMS/Email verification: 3 requests / minute / IP.
  - Payment Order Creation (`/pay/vnpay/create`): 5 requests / minute / User.
  - Author Withdrawal Request (`/author/finance/withdrawals`): 3 requests / minute / Author.
  - Book Commenting (`/book/addComment`): 6 requests / minute / User.
  - Novel Search (`/book/search`): 30 requests / minute / IP.

### 3.4 Health Monitoring & Disaster Recovery Procedures
- **Spring Boot Actuator Integration**:
  - Add `spring-boot-starter-actuator` to `pom.xml` in `novel-common` (or `novel-front`, `novel-admin`, `novel-crawl`).
  - Configure Actuator endpoints: `/actuator/health`, `/actuator/metrics`, `/actuator/info`, `/actuator/prometheus`.
  - Expose health indicators for MySQL, Redis, Disk Space, and Wallet Ledger integrity.
- **Docker Compose Health Checks**:
  - Add explicit healthcheck blocks to `front`, `admin`, `crawl` services in `compose.yaml`:
    ```yaml
    healthcheck:
      test: ["CMD-SHELL", "curl -f http://localhost:8083/actuator/health || exit 1"]
      interval: 15s
      timeout: 5s
      retries: 5
      start_period: 30s
    ```
- **Automated Backup & Recovery System**:
  - `scripts/backup-db.sh` (Linux) / `scripts/backup-db.ps1` (Windows): Automated single-transaction MySQL dump, gzipped with timestamp, volume backup of media/books, and sha256 checksum generation.
  - `scripts/restore-db.sh`: Controlled restore script verifying backup checksum, restoring DB, and executing verification queries.

### 3.5 Quality Assurance & Test Suite Setup
- **Unit Testing**:
  - `PitTaxCalculatorTest`: Verifies tax calculation across various income levels and thresholds.
  - `VatCalculatorTest`: Verifies VAT separation from gross recharge amounts.
  - `TotpServiceTest`: Verifies TOTP secret generation, QR URL formatting, valid window matching, and backup code hashing.
  - `RateLimitAspectTest`: Tests rate limiter triggering and window expiration using Redis mock/test container.
- **Integration Testing**:
  - `RevenueReportIntegrationTest`: Tests API end-to-end report generation and CSV/XLSX export outputs.
  - `TwoFactorAuthFlowIntegrationTest`: Verifies login block when 2FA is enabled until TOTP verification succeeds.
- **Load Testing (Concurrency & Throughput)**:
  - Create k6 load testing scripts in `scripts/load-test/`:
    - `k6-checkout-load.js`: High-concurrency VNPAY payment order creation & IPN handling.
    - `k6-reading-load.js`: High-throughput novel reading and chapter fetching under 500 VUs.
    - `k6-payout-load.js`: Concurrent author withdrawal request submissions with idempotency key checks.
- **Automated Regression Suite Script**:
  - `scripts/run-all-tests.sh` / `scripts/run-all-tests.ps1`: Automated script running unit tests, i18n checks, Docker Compose config checks, and report generation tests.

---

## 4. Database Schema Changes (DDL)

The following migration script `doc/sql/20260725_reports_security.sql` must be created to support R5 & R6:

```sql
-- Migration script for Requirement R5 (Reports, Tax & Receipts) and R6 (Security & Audit)
SET NAMES utf8mb4;

-- 1. Financial Vouchers & Receipts Table
CREATE TABLE IF NOT EXISTS `financial_voucher` (
    `id`                           bigint(20)    NOT NULL AUTO_INCREMENT,
    `voucher_no`                   varchar(64)   NOT NULL,
    `voucher_type`                 varchar(32)   NOT NULL COMMENT 'RECHARGE_RECEIPT or AUTHOR_PAYOUT_VOUCHER',
    `reference_type`               varchar(32)   NOT NULL COMMENT 'ORDER_PAY or AUTHOR_WITHDRAWAL_REQUEST',
    `reference_id`                 varchar(64)   NOT NULL,
    `payer_name`                   varchar(255)  NOT NULL,
    `payer_tax_code`               varchar(64)   DEFAULT NULL,
    `payee_name`                   varchar(255)  NOT NULL,
    `payee_tax_code`               varchar(64)   DEFAULT NULL,
    `gross_amount_vnd`             bigint(20)    NOT NULL,
    `tax_amount_vnd`               bigint(20)    NOT NULL DEFAULT 0 COMMENT 'VAT for recharge, PIT for payout',
    `net_amount_vnd`               bigint(20)    NOT NULL,
    `currency`                     varchar(8)    NOT NULL DEFAULT 'VND',
    `status`                       varchar(16)    NOT NULL DEFAULT 'ISSUED' COMMENT 'ISSUED, CANCELLED',
    `issued_at`                    datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `create_time`                  datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`                  datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_voucher_no` (`voucher_no`),
    UNIQUE KEY `uk_voucher_ref` (`reference_type`, `reference_id`),
    KEY `idx_voucher_type_time` (`voucher_type`, `issued_at`),
    CONSTRAINT `chk_voucher_amounts` CHECK (`gross_amount_vnd` >= 0 AND `net_amount_vnd` >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Chứng từ nạp tiền và phiếu chi tài chính';

-- 2. Two-Factor Authentication (2FA) Table
CREATE TABLE IF NOT EXISTS `user_2fa` (
    `id`                           bigint(20)    NOT NULL AUTO_INCREMENT,
    `user_id`                      bigint(20)    NOT NULL,
    `secret_key_ciphertext`        varchar(512)  NOT NULL,
    `is_enabled`                   tinyint(1)    NOT NULL DEFAULT 0,
    `backup_codes_json`            varchar(1024) DEFAULT NULL,
    `enabled_at`                   datetime(3)   DEFAULT NULL,
    `create_time`                  datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`                  datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_2fa_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Xác thực 2 lớp (2FA TOTP)';

-- 3. System Audit Log Table
CREATE TABLE IF NOT EXISTS `sys_audit_log` (
    `id`                           bigint(20)    NOT NULL AUTO_INCREMENT,
    `module`                       varchar(64)   NOT NULL COMMENT 'AUTH, PAYMENT, PAYOUT, KYC, CONTENT, SYSTEM',
    `event_type`                   varchar(64)   NOT NULL,
    `actor_id`                     bigint(20)    DEFAULT NULL,
    `actor_username`               varchar(100)  DEFAULT NULL,
    `actor_ip`                     varchar(64)   NOT NULL,
    `user_agent`                   varchar(500)  DEFAULT NULL,
    `request_url`                  varchar(255)  DEFAULT NULL,
    `request_params`               text          DEFAULT NULL,
    `status`                       varchar(16)   NOT NULL COMMENT 'SUCCESS or FAILURE',
    `detail`                       text          DEFAULT NULL,
    `create_time`                  datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_sys_audit_module_time` (`module`, `create_time`),
    KEY `idx_sys_audit_actor_time` (`actor_id`, `create_time`),
    KEY `idx_sys_audit_event_time` (`event_type`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Nhật ký kiểm toán bảo mật bất biến';

-- 4. Immutable Triggers for sys_audit_log
DROP TRIGGER IF EXISTS `trg_sys_audit_log_no_update`;
DROP TRIGGER IF EXISTS `trg_sys_audit_log_no_delete`;

DELIMITER $$
CREATE TRIGGER `trg_sys_audit_log_no_update`
    BEFORE UPDATE ON `sys_audit_log`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_audit_log is immutable';
END$$

CREATE TRIGGER `trg_sys_audit_log_no_delete`
    BEFORE DELETE ON `sys_audit_log`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_audit_log is immutable';
END$$
DELIMITER ;

-- 5. Admin Menu Registration for Reports & Security
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT 0, 'Báo cáo & Tài chính', 'novel/reports', 'novel:reports:view', 1, 'fa fa-line-chart', 8, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:reports:view');

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT menu_id, 'Báo cáo doanh thu & Thuế', 'novel/reports/revenue', 'novel:reports:revenue', 2, NULL, 1, NOW()
FROM `sys_menu` WHERE perms = 'novel:reports:view' LIMIT 1;

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT menu_id, 'Quản lý chứng từ', 'novel/reports/vouchers', 'novel:reports:vouchers', 2, NULL, 2, NOW()
FROM `sys_menu` WHERE perms = 'novel:reports:view' LIMIT 1;

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT menu_id, 'Nhật ký kiểm toán bảo mật', 'system/auditLog', 'sys:auditLog:view', 2, 'fa fa-shield', 3, NOW()
FROM `sys_menu` WHERE perms = 'sys:log:log' LIMIT 1;
```

---

## 5. API Endpoints Specification

### 5.1 Requirement R5: Revenue Reports, Tax & Vouchers

| Module | Endpoint | Method | Permission | Description |
|---|---|---|---|---|
| `novel-admin` | `/novel/reports/revenue/summary` | GET | `novel:reports:revenue` | Query platform/author revenue breakdown by period (`period`: `DAILY`/`MONTHLY`/`QUARTERLY`/`ANNUAL`, `startDate`, `endDate`). |
| `novel-admin` | `/novel/reports/revenue/export/csv` | GET | `novel:reports:export` | Export revenue summary report in CSV format. |
| `novel-admin` | `/novel/reports/revenue/export/xlsx` | GET | `novel:reports:export` | Export revenue summary report in XLSX format. |
| `novel-admin` | `/novel/reports/revenue/export/pdf` | GET | `novel:reports:export` | Export revenue summary statement in PDF format. |
| `novel-admin` | `/novel/reports/tax/pit-summary` | GET | `novel:reports:revenue` | Query PIT tax withholding report for author payouts. |
| `novel-admin` | `/novel/reports/tax/vat-summary` | GET | `novel:reports:revenue` | Query VAT tax report for platform VNPAY revenue. |
| `novel-admin` | `/novel/reports/vouchers/list` | GET | `novel:reports:vouchers` | List issued financial vouchers & receipts with pagination. |
| `novel-admin` | `/novel/reports/vouchers/{id}` | GET | `novel:reports:vouchers` | Get detailed voucher contents. |
| `novel-admin` | `/novel/reports/vouchers/{id}/pdf` | GET | `novel:reports:vouchers` | Download printable PDF voucher. |
| `novel-front` | `/author/finance/receipts` | GET | Authenticated Author | Author view of their payout vouchers & tax withholding statements. |

### 5.2 Requirement R6: Audit Logs, 2FA & Rate Limiting

| Module | Endpoint | Method | Auth / Permission | Description |
|---|---|---|---|---|
| `novel-front` | `/api/2fa/setup` | POST | JWT Auth | Generate 2FA TOTP secret key and QR code data URI for logged-in user/author. |
| `novel-front` | `/api/2fa/enable` | POST | JWT Auth | Submit 6-digit TOTP code to verify and enable 2FA on account. |
| `novel-front` | `/api/2fa/disable` | POST | JWT Auth + 2FA Code | Disable 2FA with current TOTP code confirmation. |
| `novel-front` | `/login/2fa` | POST | Public (Pre-Auth Token) | Complete 2FA login step using 6-digit TOTP code or backup code. |
| `novel-admin` | `/system/2fa/setup` | POST | Shiro Session | Generate 2FA TOTP secret key and QR code for admin user. |
| `novel-admin` | `/system/2fa/enable` | POST | Shiro Session | Verify and enable 2FA for admin account. |
| `novel-admin` | `/system/auditLog/list` | GET | `sys:auditLog:view` | Query security audit logs with pagination and filters (`module`, `event_type`, `actor_username`, `startDate`, `endDate`). |
| `novel-front` / `novel-admin` | `/actuator/health` | GET | Public / Internal | Spring Actuator health check endpoint returning status of DB, Redis, Disk, Ledger. |

---

## 6. Implementation Roadmap & Task Decomposition

### Phase 1: Core Framework & Database Schema Setup (Dependencies)
1. **Database Migration Script**:
   - Create `doc/sql/20260725_reports_security.sql` containing `financial_voucher`, `user_2fa`, `sys_audit_log`, triggers, and sys_menu seeds.
   - Update `compose.yaml` to include `20260725_reports_security.sql` in `migrate` container execution list.
2. **Pom.xml & Library Dependencies**:
   - Add `spring-boot-starter-actuator` to `novel-common/pom.xml`.
   - Add iText PDF or Apache PDFBox library for PDF voucher/report generation.
   - Add Apache POI library for XLSX report export.

### Phase 2: R5 Implementation (Reports, Tax & Receipts)
1. **Tax Calculation Engine (`novel-common`)**:
   - Build `PitTaxCalculatorService` & `VatTaxCalculatorService` in `novel-common`.
   - Update `AuthorFinanceReviewServiceImpl.java` in `novel-admin` to auto-populate `withheldTaxVnd` using `PitTaxCalculatorService` upon payout approval.
2. **Revenue Reporting Service (`novel-admin`)**:
   - Build `RevenueReportDao`, `RevenueReportService`, and `RevenueReportController`.
   - Build export handlers: `CsvExportUtil`, `ExcelReportExportUtil`, `PdfReportExportUtil`.
3. **Financial Voucher Service (`novel-common` / `novel-admin` / `novel-front`)**:
   - Build `FinancialVoucherDao`, `FinancialVoucherService`.
   - Hook voucher generation into `OrderServiceImpl` (on VNPAY payment completion) and `AuthorWithdrawalLifecycleServiceImpl` (on payout settlement).
   - Build voucher HTML views & PDF download endpoints.

### Phase 3: R6 Implementation (Security, Hardening & QA)
1. **Unified Audit Log System (`novel-common`, `novel-front`, `novel-admin`)**:
   - Create `@AuditLog` annotation and `AuditLogAspect` in `novel-common`.
   - Build `SysAuditLogDao` and `SysAuditLogService`.
   - Annotate all sensitive endpoints (login, password change, KYC view, payout approval, user ban, moderation).
   - Build `SecurityAuditLogController` in `novel-admin`.
2. **Two-Factor Authentication (2FA / TOTP)**:
   - Build `TotpService` (secret generation, Base32 encoding, QR code URL, TOTP verification, backup code generator).
   - Build 2FA endpoints in `novel-front` (`TwoFactorUserController`) and `novel-admin` (`TwoFactorAdminController`).
   - Modify authentication flows in `novel-front` (`UserController` login) and `novel-admin` (`LoginController`) to enforce 2FA challenge when enabled.
3. **Redis Rate Limiting (`novel-common`)**:
   - Create `@RateLimit` annotation and `RateLimitAspect` in `novel-common` using Redis sliding window/token bucket.
   - Apply `@RateLimit` on login, 2FA, recharge order creation, payout request, chapter publish, and search endpoints.
4. **Health Monitoring & Backup Automation**:
   - Enable Spring Actuator health indicators.
   - Update `compose.yaml` with container healthchecks for `front`, `admin`, `crawl`.
   - Create `scripts/backup-db.sh` / `scripts/backup-db.ps1` and `scripts/restore-db.sh`.
5. **Test Suite & QA Setup**:
   - Add unit tests for `PitTaxCalculatorService`, `TotpService`, `RateLimitAspect`, and `FinancialVoucherService`.
   - Create k6 load testing scripts in `scripts/load-test/k6-checkout-load.js`, `k6-reading-load.js`, `k6-payout-load.js`.
   - Create automated regression runner script `scripts/run-all-tests.sh`.

---
