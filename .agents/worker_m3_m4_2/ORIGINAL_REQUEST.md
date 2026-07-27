## 2026-07-25T05:20:42Z
<USER_REQUEST>
You are Replacement Worker 2 assigned to Milestones M3 & M4 (R3: Moderation, Content Filtering & Age Rating, R4: Copyright & Violation Management) for Novel-Plus.
Your working directory is d:\Project\novel-plus\.agents\worker_m3_m4_2.

Please create BRIEFING.md and progress.md in your working directory and execute the following tasks:

1. Database Migration:
   Create `doc/sql/20260725_moderation_copyright.sql` with DDL statements for:
   - Altering `book` table: add `age_rating` (TINYINT default 0), `audit_status` (TINYINT default 1), `audit_reason` (VARCHAR(500)).
   - Altering `book_index` table: add `audit_status` (TINYINT default 1), `content_hash` (VARCHAR(64)), `sim_hash` (BIGINT).
   - Altering `user` table: add `date_of_birth` (DATE), `is_age_verified` (TINYINT default 0).
   - Creating tables: `book_content_history`, `copyright_report`, `copyright_appeal`, `book_ownership_proof`, `sensitive_word`.

2. Core Algorithms in `novel-common`:
   - `SensitiveWordFilter`: DFA / Trie keyword matcher with Vietnamese accent normalization.
   - `SimHashUtil`: 64-bit SimHash generator & Hamming distance calculator for plagiarism detection.
   - `ContentHashUtil`: SHA-256 exact match text hasher.

3. Reader & Author Portal (`novel-front`):
   - Fix comment display defect in `novel-front/src/main/resources/mybatis/mapping/BookCommentMapper.xml` line 7 (`listCommentByPage`): add `AND t1.audit_status = 1`.
   - Implement age restriction checks (13+, 16+, 18+) against user date of birth (`date_of_birth`) in book reading/detail controllers & services.
   - Implement SHA-256 exact match + SimHash similarity checks on chapter submission to flag plagiarism/duplicates.
   - Implement chapter version snapshotting in `BookServiceImpl.addBookContent` and `updateBookContent` storing text history into `book_content_history`.
   - Implement endpoints for copyright report submission (`POST /copyright/report`), counter-appeals (`POST /copyright/appeal`), and ownership proof uploads.

4. Platform Admin Panel (`novel-admin`):
   - Build Moderation Queue endpoints & services for novels, chapters, comments, and image uploads (`/novel/moderation/*`).
   - Build Copyright Intake & Appeal management dashboard (`/novel/copyright/*`).
   - Build Plagiarism Inspection Center with side-by-side text diff comparison endpoint.

5. Test Suite & Verification:
   - Add unit & integration tests covering DFA filter, SimHash, age rating checks, comment audit filtering, chapter versioning, and copyright/appeal lifecycle.
   - Run Maven build & test command:
     `& "C:\Users\tienl\.codex\tools\apache-maven-3.9.11\bin\mvn.cmd" test -pl novel-common,novel-front,novel-admin`
   - Produce a detailed handoff.md in your working directory `d:\Project\novel-plus\.agents\worker_m3_m4_2\handoff.md`.

MANDATORY INTEGRITY WARNING:
DO NOT CHEAT. All implementations must be genuine. DO NOT hardcode test results, create dummy/facade implementations, or circumvent the intended task. A Forensic Auditor will independently verify your work. Integrity violations WILL be detected and your work WILL be rejected.

</USER_REQUEST>
