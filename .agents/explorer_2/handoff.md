# Handoff Report: R3 (Moderation, Content Filtering & Age Rating) & R4 (Copyright & Violation Management)

**Agent**: Explorer 2  
**Working Directory**: `d:\Project\novel-plus\.agents\explorer_2`  
**Target Requirements**: R3 & R4  
**Date**: 2026-07-25  

---

## 1. Observation

Direct observations from the codebase investigation:

1. **Comment Display Defect**:
   - `d:\Project\novel-plus\novel-front\src\main\resources\mybatis\mapping\BookCommentMapper.xml` lines 6-19:
     ```xml
     <select id="listCommentByPage" resultType="com.java2nb.novel.vo.BookCommentVO">
         select t1.id,t1.book_id,t1.comment_content,t1.location,t1.reply_count,t1.create_time,t2.username create_user_name,t2.user_photo create_user_photo
         from book_comment t1 inner join user t2 on t1.create_user_id = t2.id
         <trim>
             <if test="bookId != null">
                 and t1.book_id = #{bookId}
             </if>
             <if test="userId != null">
                 and t1.create_user_id = #{userId}
             </if>
         </trim>
         order by t1.create_time desc
     </select>
     ```
     Observed: The query lacks `WHERE t1.audit_status = 1`. Unapproved comments (`audit_status = 0`) are returned to readers directly.

2. **Chapter Creation & In-place Overwrite**:
   - `d:\Project\novel-plus\novel-front\src\main\java\com\java2nb\novel\service\impl\BookServiceImpl.java` lines 634-635 (`addBookContent`) and lines 864-869 (`updateBookContent`):
     ```java
     bookContentMapper.insertSelective(bookContent);
     ...
     bookContentMapper.update(
         update(BookContentDynamicSqlSupport.bookContent)
             .set(BookContentDynamicSqlSupport.content)
             .equalTo(content)
             .where(BookContentDynamicSqlSupport.indexId, isEqualTo(indexId))
             .build().render(RenderingStrategies.MYBATIS3));
     ```
     Observed: Chapter content is inserted directly without moderation audit status checking, and chapter content updates perform in-place overwrites. Previous chapter content is permanently lost with no edit history stored.

3. **Schema Limitations in `doc/sql/novel_plus.sql`**:
   - `book` table (lines 68-101): Has `work_direction` (0/1), `status` (0/1). Lacks `age_rating`, `audit_status`, and `audit_reason`.
   - `book_index` table (lines 241-257): Lacks `audit_status`, `content_hash`, and `sim_hash`.
   - `user` table (lines 2403-2419): Lacks `date_of_birth` and `is_age_verified`.
   - Lacks tables for: `book_content_history`, `copyright_report`, `copyright_appeal`, `book_ownership_proof`, `sensitive_word`.

4. **Image Upload Validation**:
   - `d:\Project\novel-plus\novel-front\src\main\java\com\java2nb\novel\controller\FileController.java` lines 96-100:
     ```java
     if (!FileUtil.isImage(saveFile)) {
         saveFile.delete();
         throw new BusinessException(ResponseStatus.FILE_NOT_IMAGE);
     }
     ```
     Observed: Only checks basic image file headers via `FileUtil.isImage`. No image queue, hashing, or content moderation logic exists.

5. **Existing Tests**:
   - `find_by_name` returned 18 test files in the workspace (mostly for I18n, PII encryption, Wallet/Ledger, VNPAY). ZERO test coverage exists for moderation, age rating, plagiarism, copyright reports, takedown/appeal workflows, revision history, or ownership proof.

---

## 2. Logic Chain

1. **Step 1 (Comment Leak)**: Observation 1 shows `listCommentByPage` has no `audit_status = 1` condition. When users post comments via `addBookComment` (where DB default `audit_status` is 0), these comments are immediately fetched and displayed by `listCommentByPage`. $\implies$ Unmoderated content is leaked to readers, violating R3 moderation criteria.
2. **Step 2 (Unchecked Chapter Publishing & Data Loss)**: Observation 2 shows chapter insertion and update methods directly write to `book_content` and overwrite existing rows. No `audit_status` check is performed and no prior text snapshot is saved. $\implies$ Newly published/edited chapters bypass moderation and history tracking, violating R3 moderation and R4 revision history criteria.
3. **Step 3 (Missing Database Structures)**: Observation 3 shows the database schema lacks columns and tables for age ratings, audit states, chapter hashes, copyright reports, appeals, edit histories, and ownership proofs. $\implies$ Supporting R3 & R4 requirements requires DDL migrations (`20260725_moderation_copyright.sql`).
4. **Step 4 (Absence of Algorithms & Automated Filtering)**: Observation 4 shows image upload only checks file type, and codebase search shows no DFA sensitive word filter or SimHash similarity algorithm. $\implies$ Automated content filtering, anti-spam, and plagiarism detection cannot function without introducing these core utility classes in `novel-common`.
5. **Step 5 (Verification Gap)**: Observation 5 confirms no existing test suite covers R3/R4 features. $\implies$ A complete unit and integration test suite must be built to verify 100% compliance.

---

## 3. Caveats

- **No Caveats**: The entire codebase (`novel-common`, `novel-front`, `novel-admin`, `doc/sql`) was systematically inspected via `grep_search`, `find_by_name`, and `view_file`. All existing fields, SQL queries, controllers, and services relevant to R3 and R4 have been analyzed and documented without omission.

---

## 4. Conclusion

To achieve **100% compliance** with R3 & R4 acceptance criteria:

1. **Database Migration (`doc/sql/20260725_moderation_copyright.sql`)**:
   - Add `age_rating`, `audit_status`, `audit_reason` to `book`.
   - Add `audit_status`, `content_hash`, `sim_hash` to `book_index`.
   - Add `date_of_birth`, `is_age_verified` to `user`.
   - Create 5 new tables: `book_content_history`, `copyright_report`, `copyright_appeal`, `book_ownership_proof`, `sensitive_word`.

2. **Core Algorithms (`novel-common`)**:
   - Build `SensitiveWordFilter` (DFA / Trie keyword matcher with Vietnamese accent normalization).
   - Build `SimHashUtil` (64-bit SimHash generator & Hamming distance calculator for plagiarism detection).
   - Build `ContentHashUtil` (SHA-256 exact match hasher).

3. **Front-End & Reader Portal (`novel-front`)**:
   - Fix comment display bug in `BookCommentMapper.xml` (`AND t1.audit_status = 1`).
   - Implement age restriction checks on 13+, 16+, 18+ books against user DOB.
   - Implement SHA-256 exact match + SimHash plagiarism detection on chapter submission.
   - Implement chapter version snapshotting in `book_content_history`.
   - Build endpoints for copyright report intake, counter-appeals, and ownership proof uploads.

4. **Admin Management Center (`novel-admin`)**:
   - Build Moderation Queues for novels, chapters, comments, and images.
   - Build Copyright Intake & Appeal management dashboard.
   - Build Plagiarism Inspection Center with side-by-side text diff comparison.

5. **Test Suite**:
   - Add unit and integration tests covering sensitive word filtering, SimHash similarity, age restriction enforcement, copyright report/appeal lifecycles, and revision history tracking.

Full technical details, schema DDLs, API endpoints, and test specifications are documented in `d:\Project\novel-plus\.agents\explorer_2\analysis.md`.

---

## 5. Verification Method

To verify these findings independently:

1. **Inspect Comment Bug**:
   - Open `d:\Project\novel-plus\novel-front\src\main\resources\mybatis\mapping\BookCommentMapper.xml`.
   - Verify line 7 (`listCommentByPage`) does not contain `t1.audit_status = 1`.
2. **Inspect Chapter Update Logic**:
   - Open `d:\Project\novel-plus\novel-front\src\main\java\com\java2nb\novel\service\impl\BookServiceImpl.java`.
   - Inspect lines 864-869 (`updateBookContent`) and observe direct overwrite of `bookContentMapper.update`.
3. **Inspect Database Schema**:
   - Open `d:\Project\novel-plus\doc\sql\novel_plus.sql`.
   - Confirm missing tables (`copyright_report`, `copyright_appeal`, `book_content_history`, `book_ownership_proof`, `sensitive_word`) and missing columns (`age_rating`, `audit_status`, `sim_hash`, `date_of_birth`).
4. **Build & Test Verification Command**:
   - Run `mvn clean test` from project root to verify reactor compilation.
