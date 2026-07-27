## 2026-07-25T04:04:40Z

You are Worker 2 implementing Milestones M3 (R3: Moderation, Content Filtering & Age Rating) and M4 (R4: Copyright & Violation Management).
Your working directory is d:\Project\novel-plus\.agents\worker_m3_m4 (create it if needed).

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

Read instructions & technical design in:
- d:\Project\novel-plus\.agents\explorer_2\analysis.md
- d:\Project\novel-plus\.agents\explorer_2\handoff.md
- d:\Project\novel-plus\.agents\ORIGINAL_REQUEST.md

Tasks:
1. Create DB migration file `doc/sql/20260725_moderation_copyright.sql` containing DDL for `book_content_history`, `copyright_report`, `copyright_appeal`, `book_ownership_proof`, `sensitive_word`, and columns for age rating (`age_rating`), audit status (`audit_status`), hashes (`content_hash`, `sim_hash`), DOB (`date_of_birth`).
2. Fix critical comment leak defect in `d:\Project\novel-plus\novel-front\src\main\resources\mybatis\mapping\BookCommentMapper.xml` line 7 (add `AND t1.audit_status = 1`).
3. Build DFA `SensitiveWordFilter`, 64-bit `SimHashUtil` for plagiarism detection, and `ContentHashUtil` (SHA-256) in `novel-common`.
4. Implement age restriction checks (13+, 16+, 18+) against user DOB in `novel-front`.
5. Implement moderation queues for novels, chapters, comments, and image uploads in `novel-front` & `novel-admin`.
6. Implement chapter version history snapshotting in `BookServiceImpl` storing previous chapter content to `book_content_history` on update.
7. Implement copyright report intake, counter-appeal workflow, and ownership proof storage with REST endpoints in `novel-front` and `novel-admin`.
8. Add comprehensive unit and integration tests for sensitive word filter, SimHash, age rating enforcement, copyright lifecycle, comment audit fix, and content history.
9. Run `mvn clean test` using run_command to verify everything compiles and passes 100%.

Deliver report in d:\Project\novel-plus\.agents\worker_m3_m4\handoff.md detailing implemented files, build output, test results, and verification commands. Send a message to parent when done.
