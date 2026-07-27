# EMPIRICAL CHALLENGE HANDOFF REPORT — MILESTONES M5-M6 (R5-R6)

**Agent**: Challenger 2 (Empirical Challenger)  
**Date**: 2026-07-25  
**Working Directory**: `d:\Project\novel-plus\.agents\challenger_2`  
**Target Project**: Novel-Plus (`novel-common`, `novel-front`, `novel-admin`)

---

## 1. Observation

### 1.1 PIT Tax Calculation Engine (`PitTaxCalculator.java` / `PitTaxCalculatorService.java`)
- **File & Line**: `novel-common/src/main/java/com/java2nb/novel/common/tax/PitTaxCalculator.java` (lines 37-40) and `PitTaxCalculatorService.java` (lines 54-57):
  ```java
  if (grossAmountVnd >= exemptionThresholdVnd) {
      taxableAmountVnd = grossAmountVnd;
      withheldTaxVnd = Math.round(grossAmountVnd * taxRate);
  }
  ```
- **Empirical Execution Results**:
  - `grossAmountVnd = 1,999,999 VND`: `withheldTaxVnd = 0 VND`, `netAmountVnd = 1,999,999 VND` (Tax exempt).
  - `grossAmountVnd = 2,000,000 VND`: `withheldTaxVnd = 200,000 VND`, `netAmountVnd = 1,800,000 VND`.
  - `grossAmountVnd = 2,000,001 VND`: `withheldTaxVnd = 200,000 VND` (`Math.round(2000001 * 0.10)`), `netAmountVnd = 1,800,001 VND`.
  - `grossAmountVnd = 5,000,000 VND`: `withheldTaxVnd = 500,000 VND`, `netAmountVnd = 4,500,000 VND`.
- **Discrepancy Observation**: User specification #1 states income at **2,000,000 VND should yield tax=0**, while 2,000,001 VND yields tax=200,000. However, the implementation uses `>= exemptionThresholdVnd` (`2,000,000 >= 2,000,000` evaluates to true), charging 200,000 VND tax on 2,000,000 VND earnings.

### 1.2 VAT Separation Calculations for Top-ups (`VatTaxCalculatorService.java`)
- **File & Line**: `novel-common/src/main/java/com/java2nb/novel/common/tax/VatTaxCalculatorService.java` (lines 45-46):
  ```java
  long netAmountVnd = Math.round((double) grossAmountVnd / (1.0 + vatRate));
  long vatAmountVnd = grossAmountVnd - netAmountVnd;
  ```
- **Empirical Results**:
  - `110,000 VND` (Gross): Net = `100,000 VND`, VAT = `10,000 VND` (10% standard rate).
  - `100,000 VND` (Gross): Net = `90,909 VND`, VAT = `9,091 VND`.
  - `220,000 VND` (Gross): Net = `200,000 VND`, VAT = `20,000 VND`.
  - `0 VND` (Gross): Net = `0 VND`, VAT = `0 VND`.
- **Invariant Check**: Invariant `Gross == Net + VAT` holds 100% across all tested inputs.

### 1.3 Financial Voucher Generation & Checksum (`FinancialVoucherServiceImpl.java`)
- **File & Line**: `novel-common/src/main/java/com/java2nb/novel/common/service/impl/FinancialVoucherServiceImpl.java` (line 241 & lines 172, 250):
  ```java
  int randomNum = new Random().nextInt(90000) + 10000;
  return prefix + "-" + dateStr + "-" + randomNum;
  ```
- **Empirical Numbering Collision Test**: Running 10,000 invocations of `generateVoucherNo("INV")` produced repeated collisions (duplicate voucher numbers) due to the small 5-digit pseudo-random space (90,000 possibilities per day per prefix). In a database environment with a `UNIQUE` index on `voucher_no`, this causes transaction failures (`DuplicateKeyException`).
- **Checksum Integrity**: SHA-256 computation over `voucherNo|grossAmountVnd|netAmountVnd` produces valid hex digests embedded in PDF outputs (e.g. `Checksum: <sha256_hash>`).

### 1.4 `sys_audit_log` Immutability Triggers (`20260725_reports_security.sql`)
- **File & Line**: `doc/sql/20260725_reports_security.sql` (lines 70-82):
  ```sql
  CREATE TRIGGER `trg_sys_audit_log_no_update`
      BEFORE UPDATE ON `sys_audit_log` FOR EACH ROW
  BEGIN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_audit_log is immutable';
  END;

  CREATE TRIGGER `trg_sys_audit_log_no_delete`
      BEFORE DELETE ON `sys_audit_log` FOR EACH ROW
  BEGIN
      SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_audit_log is immutable';
  END;
  ```
- **DB Verification**: Database triggers abort any `UPDATE` or `DELETE` statement against `sys_audit_log` with SQLSTATE '45000', enforcing append-only immutability.

### 1.5 TOTP RFC 6238 Key Generation, Skew Window, & QR Formatting (`TotpServiceImpl.java`)
- **File & Line**: `novel-common/src/main/java/com/java2nb/novel/common/service/impl/TotpServiceImpl.java` (lines 34-60, 229-250):
  - Secret Generation: 20-byte (160-bit) `SecureRandom` key encoded to 32 Base32 characters matching `[A-Z2-7]`.
  - Skew Window: Checks `currentWindow - 1`, `currentWindow`, `currentWindow + 1` (±30s tolerance). Empirically verified: T-30s, T, T+30s pass; T-60s and T+60s fail.
  - QR URI Format: `otpauth://totp/NovelPlus:username?secret=KEY&issuer=NovelPlus`.

### 1.6 RateLimit Aspect Sliding Window & Fallback (`RateLimitAspect.java`)
- **File & Line**: `novel-common/src/main/java/com/java2nb/novel/common/aspect/RateLimitAspect.java` (lines 51-68, 143-166):
- **Redis Fallback**: When `redisTemplate` throws an exception (simulating Redis server failure), `RateLimitAspect` catches the exception and falls back to in-memory `SlidingWindowCounter`.
- **Sliding Window Counter Defect**: `SlidingWindowCounter.allow(maxCount)` uses fixed-window logic (`now - windowStartMs > timeWindowSeconds * 1000L`). When the window expires, `count` resets to 0. Under boundary burst conditions (e.g. 5 requests at end of window, 5 requests at start of next window), up to 2x `maxCount` requests are allowed within a time window.

### 1.7 Test Suite Execution Results
- **Command Executed**: `& "C:\Users\tienl\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd" test -pl novel-common,novel-front,novel-admin`
- **Module Results**:
  - `novel-common`: **BUILD SUCCESS** (42 tests passed, 0 failures, 0 errors).
  - `novel-front`: **BUILD FAILURE** due to compilation error in `DoubleEntryLedgerChallengeTest.java:154` (`cannot find symbol variable Amount` on type `WalletEntryRow`).
  - `novel-admin`: **SKIPPED** due to build failure in `novel-front`.

---

## 2. Logic Chain

1. **PIT Tax Threshold Logic**:
   - *Observation*: `PitTaxCalculator.java:37` evaluates `grossAmountVnd >= exemptionThresholdVnd`.
   - *Reasoning*: For 2,000,000 VND, `2,000,000 >= 2,000,000` is true, executing `withheldTaxVnd = Math.round(2,000,000 * 0.10) = 200,000 VND`. The prompt specification expected tax=0 at 2,000,000 VND and tax=200,000 VND at 2,000,001 VND. The code implements inclusive threshold (`>=`) rather than exclusive threshold (`>`).
2. **Voucher Numbering Collision Risk**:
   - *Observation*: `generateVoucherNo` appends `new Random().nextInt(90000) + 10000`.
   - *Reasoning*: With only 90,000 possible suffixes per day, generating multiple vouchers per day has a high probability of collision (birthday paradox). 10,000 iterations in unit test produced dozens of duplicate voucher numbers.
3. **RateLimit In-Memory Window Vulnerability**:
   - *Observation*: `SlidingWindowCounter` checks `if (now - windowStartMs > timeWindowSeconds * 1000L) { windowStartMs = now; count = 0; }`.
   - *Reasoning*: Resetting `count = 0` at window boundaries is tumbling/fixed window behavior, not true sliding window. High concurrency at boundary transitions bypasses the intended rate limit rate.
4. **Test Suite Failure**:
   - *Observation*: Maven compiler failed on `DoubleEntryLedgerChallengeTest.java:154`.
   - *Reasoning*: Reference to non-existent field `entry.Amount` instead of `entry.getAmountVnd()` breaks compilation of `novel-front`, preventing execution of `novel-front` and `novel-admin` test suites.

---

## 3. Caveats

- **DB Triggers**: Database immutability triggers were verified by static SQL code analysis (`20260725_reports_security.sql`). Direct live MySQL engine execution requires an active database instance.
- **Redis Fallback**: Tested via Mockito stubbing of `StringRedisTemplate` throwing `RuntimeException` to verify fallback path code execution.

---

## 4. Conclusion

- **PIT Tax Engine**: **FAIL (SPEC MISMATCH)** — Income of 2,000,000 VND is taxed 200,000 VND instead of being exempt (0 VND tax).
- **VAT Separation**: **PASS** — Accurately separates Net and VAT, maintaining `Gross == Net + VAT`.
- **Financial Voucher**: **PASS (WITH WARNING)** — SHA-256 checksum integrity is valid, but voucher number generation is vulnerable to collisions.
- **`sys_audit_log` Immutability**: **PASS** — SQL triggers block UPDATE and DELETE with SQLSTATE 45000.
- **TOTP RFC 6238**: **PASS** — 160-bit key generation, ±30s skew window, and QR URI formatting strictly adhere to RFC 6238.
- **RateLimit Aspect**: **PASS (WITH WARNING)** — Redis fallback functions properly, but in-memory counter is fixed-window rather than sliding-window.
- **Maven Test Suites**: **FAIL** — `novel-front` has a test file compilation error (`DoubleEntryLedgerChallengeTest.java:154`).

---

## 5. Verification Method

To independently verify these empirical results:

1. **Run Custom Empirical Challenge Test Suite**:
   ```powershell
   & "C:\Users\tienl\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd" test -Dtest=Milestone56EmpiricalChallengeTest -pl novel-common
   ```
2. **Run Full Project Test Suite**:
   ```powershell
   & "C:\Users\tienl\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd" test -pl novel-common,novel-front,novel-admin
   ```
3. **Inspect Implementation Files**:
   - `novel-common/src/main/java/com/java2nb/novel/common/tax/PitTaxCalculator.java`
   - `novel-common/src/main/java/com/java2nb/novel/common/service/impl/FinancialVoucherServiceImpl.java`
   - `novel-common/src/main/java/com/java2nb/novel/common/aspect/RateLimitAspect.java`
   - `novel-front/src/test/java/com/java2nb/novel/service/impl/DoubleEntryLedgerChallengeTest.java` (Line 154)
