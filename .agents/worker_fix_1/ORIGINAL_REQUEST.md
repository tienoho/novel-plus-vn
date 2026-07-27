## 2026-07-26T01:20:28Z
You are Worker Fix 1 (teamwork_preview_worker).
Working Directory: d:\Project\novel-plus\.agents\worker_fix_1

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Your Objective:
Fix the 5 critical defects across M2, M3, M4, M5, and the test suite compilation in Novel-Plus, then run the full Maven test suite to verify 100% pass.

Defect Details & Instructions:

1. VietQR Adapter (M2):
   - File: `novel-common/src/main/java/com/java2nb/novel/core/utils/VietQrGeneratorUtil.java`
   - Issue: `computeCrc16CcittFalse(String str)` uses `str.getBytes(StandardCharsets.US_ASCII)`. Payments with Vietnamese diacritics fail CRC validation.
   - Fix: Replace `StandardCharsets.US_ASCII` with `StandardCharsets.UTF_8`.

2. SensitiveWordFilter (M3):
   - File: `novel-common/src/main/java/com/java2nb/novel/core/utils/SensitiveWordFilter.java`
   - Issue: DFA trie traversal skips whitespace during detection (e.g. "đả  o chính" matches "đảo chính"), but `filter()` uses `replaceAll("(?i)" + Pattern.quote(word), rep)`, which fails to mask text containing internal whitespace/spaces.
   - Fix: Update `filter()` method so that when sensitive words are detected (including matches with internal whitespace/punctuation skipped by DFA), the actual character span in the original text is masked with replacement characters (e.g., '*').

3. AgeRatingUtil (M4):
   - File: `novel-common/src/main/java/com/java2nb/novel/core/utils/AgeRatingUtil.java`
   - Issue: Comparing `Calendar.DAY_OF_YEAR` causes a -1 year age calculation error on birthdays for users born in leap years (Dec 31 is day 366 in leap year vs day 365 in non-leap year).
   - Fix: Replace `Calendar.DAY_OF_YEAR` comparison with proper `Calendar.MONTH` and `Calendar.DAY_OF_MONTH` checks (or `java.time.Period.between()`), ensuring exact birthdays evaluate correctly.

4. PIT Tax Calculator (M5):
   - File: `novel-common/src/main/java/com/java2nb/novel/common/tax/PitTaxCalculator.java` (and `PitTaxCalculatorService.java` if applicable)
   - Issue: Evaluates `grossAmountVnd >= exemptionThresholdVnd` charging 200,000 VND tax on exact 2,000,000 VND earnings. Income up to 2,000,000 VND should be exempt (tax = 0), and > 2,000,000 VND subject to tax.
   - Fix: Change `>= exemptionThresholdVnd` to `> exemptionThresholdVnd`.

5. QA / Test Suite Compilation:
   - File: `novel-front/src/test/java/com/java2nb/novel/service/impl/DoubleEntryLedgerChallengeTest.java` (line 154)
   - Issue: Compilation error `cannot find symbol variable Amount` on `WalletEntryRow`.
   - Fix: Replace `entry.Amount` with `entry.getAmountVnd()`.

Verification Steps:
1. Make all 5 source fixes cleanly.
2. Run the full project test suite using Maven:
   `& "C:\Users\tienl\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd" test -pl novel-common,novel-front,novel-admin`
3. Run the empirical challenge tests:
   `& "C:\Users\tienl\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd" test -Dtest=DoubleEntryLedgerChallengeTest,VietQrGeneratorChallengeTest,SensitiveWordFilterChallengeTest,SimHashUtilChallengeTest,AgeRatingUtilChallengeTest,Milestone56EmpiricalChallengeTest -pl novel-common,novel-front`
4. Write a handoff report to `d:\Project\novel-plus\.agents\worker_fix_1\handoff.md` with:
   - Observation: Exact changes made per file and verbatim test output proving 100% build & test pass.
   - Logic Chain: Rationale for each fix.
   - Caveats: Any observations.
   - Conclusion: PASS status for all 5 defects.
   - Verification Method: Exact commands to rerun tests.
5. Send a completion message back to parent.
