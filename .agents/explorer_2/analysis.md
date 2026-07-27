# Detailed Technical Investigation: Requirements R3 & R4

**System**: Novel-Plus  
**Module**: novel-common, novel-front, novel-admin, doc/sql  
**Investigator**: Explorer 2  
**Date**: 2026-07-25  

---

## Executive Summary

This report presents a thorough technical investigation of **Requirement R3 (Moderation, Content Filtering & Age Rating)** and **Requirement R4 (Copyright & Violation Management)** for the Novel-Plus platform. 

The current codebase contains initial primitives for basic comment management (`audit_status` field on `book_comment`), basic image upload validation (file extension check), and basic author KYC (from recent payout migrations). However, there are significant gaps in moderation workflows, content age rating enforcement, anti-spam/plagiarism algorithms, copyright report intake, takedown/appeal lifecycles, chapter edit versioning, and ownership proof storage.

Crucially, a **critical bug** was identified in comment display: `BookCommentMapper.xml` line 7 (`listCommentByPage`) fails to filter comments by `audit_status = 1`, causing unapproved or pending comments to be exposed directly to public readers.

Below is the exhaustive architectural breakdown, current state findings, gap analysis, required database migrations, API endpoints to build/update, and testing strategy required to achieve 100% compliance with R3 and R4 acceptance criteria.

---

## 1. Requirement R3: Moderation, Content Filtering & Age Rating

### 1.1 Current State Analysis

#### Moderation Queues
1. **Comment & Reply Moderation**:
   - Schema: `book_comment` and `book_comment_reply` tables contain an `audit_status` column (`0`: pending, `1`: approved, `2`: rejected).
   - Defect (`novel-front`): In `BookCommentMapper.xml` line 7 (`listCommentByPage`), the SQL query selects comments without filtering by `audit_status = 1`. Unapproved/pending comments are immediately visible to all portal users upon posting via `BookController.addBookComment`.
   - Missing: Automated sensitive keyword scanning prior to DB insertion; bulk moderation queue in `novel-admin` with status filtering and keyword highlighting.
2. **Novel Metadata Moderation**:
   - Schema: `book` table has a `status` column (`0`: in stock/draft, `1`: listed/published). 
   - Defect: There is no dedicated moderation state (`audit_status` like `0`: Pending Review, `1`: Approved, `2`: Rejected, `3`: Taken Down) or moderation reason tracking. Authors can publish or modify books without administrative review queues.
3. **Chapter Content Moderation**:
   - Schema: `book_index` (chapters) has NO `audit_status` or `pass_status` column.
   - Defect: `AuthorController.addBookContent` and `updateBookContent` insert/update chapter text directly into `book_index` and `book_content` without an audit queue. Newly posted or edited chapters are immediately readable by the public.
4. **Image Upload Moderation**:
   - Implementation: `FileController.java` (`novel-front` line 78) and `FileController.java` (`novel-admin` line 148).
   - Defect: Only standard file extension validation (`FileUtil.isImage`) is performed. There is NO image moderation queue, NO image perceptual hashing (pHash/MD5), NO adult/NSFW content detection, and NO audit status for uploaded covers or avatars.

#### Content Rating Classification & Age Restrictions
- **Current State**:
  - `book` table only tracks `work_direction` (0: Male, 1: Female) and `cat_id`/`cat_name`.
  - NO content age classification attributes exist (e.g., `ALL` (0+), `TEEN` (13+), `MATURE_16` (16+), `ADULT_18` (18+)).
  - `user` table (lines 2403-2419 in `doc/sql/novel_plus.sql`) lacks `date_of_birth`, `age`, or `is_age_verified` fields (only author KYC encrypts DOB for financial payout compliance, not general reader access).
  - NO age restriction enforcement logic exists in `BookController`, `ChapterController`, or `PageController`. Underaged readers or anonymous guests can view all content unrestricted.

#### Anti-Spam, Duplicate Novel/Chapter & Plagiarism Detection
- **Current State**:
  - Novel duplicate check: Only relies on DB UNIQUE key `key_uq_bookName_authorName` on `(book_name, author_name)` and `queryIdByNameAndAuthor` in `BookServiceImpl.java` line 526.
  - Rate Limiting: NO rate limiting on comment posting, chapter publishing, or file uploads. Users can flood comments or chapters without throttling.
  - Chapter Duplicate & Plagiarism Detection: NO exact text hashing (SHA-256/MD5), NO SimHash / MinHash / N-gram similarity algorithms, and NO text fingerprinting exists to detect copy-pasted or plagiarized chapters.

---

## 2. Requirement R4: Copyright & Violation Management

### 2.1 Current State Analysis

#### Copyright Infringement Report Intake
- **Current State**:
  - Only a primitive `user_feedback` table exists (`id`, `user_id`, `content`, `create_time`).
  - Missing: A dedicated DMCA / Copyright Infringement Intake System. There are no tables, DTOs, or endpoints for reporting copyright violations with contact details, target content URLs, infringement types, and evidence attachment uploads.

#### Content Takedown & Appeal Workflows
- **Current State**:
  - Admins can manually delete or update `book.status`, but there is no formal takedown notice system, author notification, or appeal workflow.
  - Missing:
    - Formal Takedown Action: Setting novel/chapter status to `TAKEN_DOWN` (hiding from public read & search while retaining data for legal compliance).
    - Author Notification & Appeal Intake: Author receives a formal notice and can submit a counter-appeal with evidence within a designated appeal window.
    - Admin Appeal Audit & Content Restoration: Workflow for admins to evaluate counter-appeals and either restore content or finalize the takedown.

#### Revision History Tracking (Novel & Chapter Edits)
- **Current State**:
  - When an author or admin updates chapter content (`BookServiceImpl.java` line 864 `updateBookContent`), the content in `book_content` is overwritten in place.
  - Defect: Previous chapter text is permanently lost. There is no versioning, no edit log, no diff tracking, and no audit trail for modified content.

#### Ownership Proof Evidence Storage
- **Current State**:
  - `author_kyc_profile` (created in `20260718_author_payout.sql`) stores author identity and bank details for payouts, but does NOT store novel ownership proof.
  - Missing: A dedicated repository (`book_ownership_proof`) for uploading and verifying copyright certificates, publishing contracts, original timestamped manuscripts, or author authorization documents.

---

## 3. Required Database Schema Changes

A new migration script `doc/sql/20260725_moderation_copyright.sql` must be created with the following DDL statements:

```sql
-- Migration: Moderation, Content Filtering, Age Rating & Copyright Management
SET NAMES utf8mb4;

-- 1. Add Age Rating & Audit Status to Book
ALTER TABLE `book` 
  ADD COLUMN `age_rating` TINYINT NOT NULL DEFAULT 0 COMMENT 'Giới hạn độ tuổi: 0: Tất cả, 13: 13+, 16: 16+, 18: 18+',
  ADD COLUMN `audit_status` TINYINT NOT NULL DEFAULT 1 COMMENT 'Trạng thái kiểm duyệt: 0: Chờ duyệt, 1: Đã duyệt, 2: Từ chối, 3: Gỡ bài (Takedown)',
  ADD COLUMN `audit_reason` VARCHAR(500) DEFAULT NULL COMMENT 'Lý do kiểm duyệt / gỡ bài',
  ADD INDEX `idx_book_age_audit` (`age_rating`, `audit_status`);

-- 2. Add Audit Status & Content Fingerprints to Book Index (Chapters)
ALTER TABLE `book_index` 
  ADD COLUMN `audit_status` TINYINT NOT NULL DEFAULT 1 COMMENT 'Trạng thái kiểm duyệt: 0: Chờ duyệt, 1: Đã duyệt, 2: Từ chối, 3: Gỡ bài',
  ADD COLUMN `content_hash` CHAR(64) DEFAULT NULL COMMENT 'Mã băm SHA-256 nội dung chương',
  ADD COLUMN `sim_hash` CHAR(64) DEFAULT NULL COMMENT 'SimHash 64-bit fingerprint cho lọc trùng',
  ADD INDEX `idx_index_audit` (`audit_status`),
  ADD INDEX `idx_content_hash` (`content_hash`);

-- 3. Add Date of Birth & Age Verification to User Table
ALTER TABLE `user` 
  ADD COLUMN `date_of_birth` DATE DEFAULT NULL COMMENT 'Ngày sinh người dùng',
  ADD COLUMN `is_age_verified` TINYINT NOT NULL DEFAULT 0 COMMENT 'Đã xác minh độ tuổi: 0: Chưa, 1: Đã xác minh';

-- 4. Create Chapter Content Revision History Table
CREATE TABLE IF NOT EXISTS `book_content_history` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID lịch sử',
  `book_id` BIGINT(20) NOT NULL COMMENT 'ID tác phẩm',
  `index_id` BIGINT(20) NOT NULL COMMENT 'ID chương',
  `version_num` INT NOT NULL COMMENT 'Số phiên bản chỉnh sửa (1, 2, 3...)',
  `index_name` VARCHAR(100) NOT NULL COMMENT 'Tên chương tại phiên bản này',
  `content` MEDIUMTEXT NOT NULL COMMENT 'Nội dung chương tại phiên bản này',
  `word_count` INT NOT NULL COMMENT 'Số từ',
  `content_hash` CHAR(64) NOT NULL COMMENT 'Mã băm SHA-256 nội dung',
  `modified_by` BIGINT(20) NOT NULL COMMENT 'ID người thực hiện chỉnh sửa',
  `modified_type` TINYINT NOT NULL DEFAULT 1 COMMENT 'Loại chỉnh sửa: 1: Tác giả sửa, 2: Quản trị viên sửa, 3: Hệ thống',
  `change_reason` VARCHAR(255) DEFAULT NULL COMMENT 'Lý do thay đổi',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Thời gian tạo phiên bản',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_index_version` (`index_id`, `version_num`),
  KEY `idx_history_book_index` (`book_id`, `index_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Lịch sử phiên bản chỉnh sửa chương';

-- 5. Create Copyright Infringement Report Intake Table
CREATE TABLE IF NOT EXISTS `copyright_report` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID báo cáo',
  `report_no` VARCHAR(32) NOT NULL COMMENT 'Mã báo cáo duy nhất (e.g. CR202607250001)',
  `reporter_id` BIGINT(20) DEFAULT NULL COMMENT 'ID người dùng báo cáo (nếu đã đăng nhập)',
  `reporter_name` VARCHAR(100) NOT NULL COMMENT 'Họ tên người báo cáo / Đại diện pháp lý',
  `reporter_email` VARCHAR(100) NOT NULL COMMENT 'Email liên hệ',
  `reporter_phone` VARCHAR(20) DEFAULT NULL COMMENT 'Số điện thoại',
  `reporter_type` TINYINT NOT NULL DEFAULT 1 COMMENT 'Loại người báo cáo: 1: Tác giả gốc, 2: Chủ sở hữu bản quyền, 3: Đại diện ủy quyền',
  `target_type` TINYINT NOT NULL DEFAULT 1 COMMENT 'Đối tượng vi phạm: 1: Tác phẩm, 2: Chương, 3: Ảnh bìa',
  `target_id` BIGINT(20) NOT NULL COMMENT 'ID đối tượng bị báo cáo (book_id / index_id)',
  `target_name` VARCHAR(200) NOT NULL COMMENT 'Tên đối tượng bị báo cáo',
  `original_work_name` VARCHAR(200) NOT NULL COMMENT 'Tên tác phẩm gốc bị xâm phạm',
  `original_work_url` VARCHAR(500) DEFAULT NULL COMMENT 'Link tác phẩm gốc / đăng ký bản quyền',
  `violation_type` TINYINT NOT NULL DEFAULT 1 COMMENT 'Loại vi phạm: 1: Sao chép không xin phép, 2: Đạo văn/Phóng tác, 3: Xâm phạm nhãn hiệu',
  `description` TEXT NOT NULL COMMENT 'Mô tả chi tiết hành vi vi phạm',
  `evidence_urls` VARCHAR(2000) DEFAULT NULL COMMENT 'JSON danh sách URL bằng chứng đính kèm',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT 'Trạng thái: 0: Chờ xử lý, 1: Đang thụ lý, 2: Đã duyệt gỡ bài, 3: Bác bỏ, 4: Đã kháng nghị, 5: Hoàn tất',
  `review_result` VARCHAR(1000) DEFAULT NULL COMMENT 'Kết quả xử lý của quản trị viên',
  `reviewer_id` BIGINT(20) DEFAULT NULL COMMENT 'ID quản trị viên xử lý',
  `reviewed_at` DATETIME DEFAULT NULL COMMENT 'Thời gian xử lý',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT 'Thời gian tạo báo cáo',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_report_no` (`report_no`),
  KEY `idx_report_status` (`status`),
  KEY `idx_report_target` (`target_type`, `target_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Báo cáo vi phạm bản quyền';

-- 6. Create Copyright Appeal Table
CREATE TABLE IF NOT EXISTS `copyright_appeal` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID kháng nghị',
  `report_id` BIGINT(20) NOT NULL COMMENT 'ID báo cáo vi phạm liên quan',
  `book_id` BIGINT(20) NOT NULL COMMENT 'ID tác phẩm',
  `author_id` BIGINT(20) NOT NULL COMMENT 'ID tác giả kháng nghị',
  `appeal_reason` TEXT NOT NULL COMMENT 'Lý do và giải trình kháng nghị',
  `proof_urls` VARCHAR(2000) DEFAULT NULL COMMENT 'JSON danh sách URL bằng chứng phản hồi',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT 'Trạng thái: 0: Chờ duyệt, 1: Chấp nhận (Phục hồi bài), 2: Bác bỏ',
  `review_remark` VARCHAR(1000) DEFAULT NULL COMMENT 'Nhận xét của quản trị viên',
  `reviewer_id` BIGINT(20) DEFAULT NULL COMMENT 'ID quản trị viên duyệt',
  `reviewed_at` DATETIME DEFAULT NULL COMMENT 'Thời gian duyệt',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_appeal_report` (`report_id`),
  KEY `idx_appeal_author` (`author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Kháng nghị gỡ bài bản quyền';

-- 7. Create Novel Ownership Proof Table
CREATE TABLE IF NOT EXISTS `book_ownership_proof` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT COMMENT 'ID bằng chứng',
  `book_id` BIGINT(20) NOT NULL COMMENT 'ID tác phẩm',
  `author_id` BIGINT(20) NOT NULL COMMENT 'ID tác giả',
  `proof_type` TINYINT NOT NULL DEFAULT 1 COMMENT 'Loại bằng chứng: 1: Giấy chứng nhận bản quyền, 2: Hợp đồng xuất bản/ủy quyền, 3: Bản thảo gốc có dấu thời gian, 4: Khác',
  `file_url` VARCHAR(500) NOT NULL COMMENT 'Đường dẫn tệp bằng chứng',
  `file_hash` CHAR(64) NOT NULL COMMENT 'Mã băm SHA-256 của tệp bằng chứng',
  `note` VARCHAR(500) DEFAULT NULL COMMENT 'Ghi chú bổ sung',
  `verification_status` TINYINT NOT NULL DEFAULT 0 COMMENT 'Trạng thái xác minh: 0: Chờ xác minh, 1: Hợp lệ, 2: Không hợp lệ',
  `verifier_id` BIGINT(20) DEFAULT NULL COMMENT 'ID quản trị viên xác minh',
  `verified_at` DATETIME DEFAULT NULL COMMENT 'Thời gian xác minh',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_proof_book` (`book_id`),
  KEY `idx_proof_author` (`author_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Bằng chứng sở hữu nội dung tác phẩm';

-- 8. Create Sensitive Word Table
CREATE TABLE IF NOT EXISTS `sensitive_word` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `word` VARCHAR(100) NOT NULL COMMENT 'Từ nhạy cảm',
  `category` VARCHAR(50) DEFAULT 'GENERAL' COMMENT 'Phân loại: POLITICS, PORN, VIOLENCE, ADVERTISING, GENERAL',
  `replacement` VARCHAR(50) DEFAULT '***' COMMENT 'Từ thay thế mặc định',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT 'Trạng thái: 1: Hoạt động, 0: Vô hiệu',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_word` (`word`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Từ điển từ ngữ nhạy cảm';
```

---

## 4. API Endpoints & Software Component Specifications

### 4.1 `novel-common` Core Components

1. **Algorithms & Utilities**:
   - `SensitiveWordFilter`: DFA (Deterministic Finite Automaton) / Trie-based keyword matching service supporting accent normalization for Vietnamese text, profanity masking (`***`), and sensitivity score calculation.
   - `SimHashUtil`: 64-bit SimHash generator extracting N-gram features from chapter content, computing 64-bit integer fingerprints, and checking Hamming distances ($d \le 3 \implies \text{Similarity} > 85\%$).
   - `ContentHashUtil`: SHA-256 hashing utility for exact duplicate detection and file tamper-proofing.
2. **Enums & DTOs**:
   - `AgeRatingEnum`: `ALL(0)`, `TEEN_13(13)`, `MATURE_16(16)`, `ADULT_18(18)`.
   - `ModerationStatusEnum`: `PENDING(0)`, `APPROVED(1)`, `REJECTED(2)`, `TAKEN_DOWN(3)`.
   - `CopyrightReportStatusEnum`: `PENDING(0)`, `UNDER_REVIEW(1)`, `APPROVED_TAKEDOWN(2)`, `REJECTED(3)`, `APPEALED(4)`, `RESOLVED(5)`.

### 4.2 `novel-front` API Endpoints & Business Logic Updates

1. **Comment Display Bug Fix**:
   - Fix `BookCommentMapper.xml` line 7 (`listCommentByPage`) to include `AND t1.audit_status = 1`.
   - Update `BookServiceImpl.addBookComment`: Pass text through `SensitiveWordFilter`. If clean -> set `audit_status = 1`. If sensitive -> set `audit_status = 0` (Pending Admin Review).
2. **Age Rating Enforcement**:
   - Update `BookController` / `PageController` (`queryBookDetail`, `queryIndexContent`, `listBookPage`): Check `book.age_rating` against user's birthdate (`user.date_of_birth`).
   - If user age < restriction or anonymous reader accessing 18+ content -> return `RestResult.error(ResponseStatus.AGE_RESTRICTED)` with explicit warning screen.
3. **Anti-Spam & Rate Limiting**:
   - Apply Redis-backed `@RateLimit` annotation on `addBookComment` (e.g. 1 comment per 10s per user) and `addBookContent` (e.g. 10 chapters per hour per author).
4. **Duplicate Chapter & Plagiarism Detection**:
   - In `BookServiceImpl.addBookContent` and `updateBookContent`:
     - Calculate SHA-256 `content_hash` and 64-bit `sim_hash`.
     - Check exact `content_hash` against existing chapters -> throw `CHAPTER_DUPLICATE_EXISTS` if exact match.
     - Calculate SimHash Hamming distance against existing chapters in same/other books -> if Hamming distance $\le 3$, set chapter `audit_status = 0` and log `SUSPECTED_PLAGIARISM` in moderation queue.
5. **Chapter Revision History**:
   - In `addBookContent` and `updateBookContent`: Create snapshot record in `book_content_history` with incremented `version_num`.
   - Endpoints for Authors:
     - `GET /author/chapterHistory/{indexId}`: List version history of a chapter.
     - `GET /author/chapterHistory/compare?v1={v1}&v2={v2}`: Compare content diff between two versions.
6. **Copyright Report & Appeal Intake**:
   - `POST /copyright/report`: Submit infringement report (form fields + evidence attachment uploads).
   - `GET /copyright/report/status?reportNo={no}`: Check report progress.
   - `POST /author/copyright/appeal`: Author submits counter-appeal against content takedown.
   - `POST /author/ownershipProof/upload`: Author uploads copyright certificate or manuscript proof file.

### 4.3 `novel-admin` Platform Management Endpoints

1. **Moderation Center**:
   - `BookModerationController`:
     - `GET /novel/moderation/book/list`: Query pending/flagged novels (`audit_status = 0`).
     - `POST /novel/moderation/book/audit`: Approve or Reject novel with audit reason.
     - `GET /novel/moderation/chapter/list`: Query pending/flagged chapters.
     - `POST /novel/moderation/chapter/audit`: Approve or Reject chapter.
   - `CommentModerationController`:
     - `GET /novel/moderation/comment/list`: Query pending/rejected comments (`audit_status IN (0, 2)`).
     - `POST /novel/moderation/comment/batchAudit`: Batch approve or reject selected comments.
   - `SensitiveWordController`:
     - `GET /novel/sensitiveWord/list`: Manage sensitive dictionary.
     - `POST /novel/sensitiveWord/save`, `/update`, `/remove`.
2. **Copyright & Violation Management**:
   - `CopyrightReportController`:
     - `GET /novel/copyrightReport/list`: Search and list copyright reports by status.
     - `POST /novel/copyrightReport/audit`: Process report (Approve Takedown / Reject).
     - `GET /novel/copyrightAppeal/list`: View author appeals.
     - `POST /novel/copyrightAppeal/audit`: Approve appeal (Restore Content) or Reject appeal.
   - `OwnershipProofController`:
     - `GET /novel/ownershipProof/list`: Search author ownership proof uploads.
     - `POST /novel/ownershipProof/verify`: Verify/reject ownership evidence.
3. **Plagiarism Inspection Center**:
   - `GET /novel/plagiarism/flagged`: List chapters flagged by SimHash similarity.
   - `GET /novel/plagiarism/diff`: Side-by-side text diff comparison tool for admins.

---

## 5. Required Test Plan & Test Cases

To guarantee 100% compliance with R3 and R4 acceptance criteria, the following unit and integration tests must be implemented:

1. **`SensitiveWordFilterTest.java`** (`novel-common`):
   - Test exact keyword matching, case insensitivity, Vietnamese accent handling, and masking (`***`).
2. **`SimHashUtilTest.java`** (`novel-common`):
   - Test SimHash fingerprint generation, Hamming distance calculation, and verify similarity thresholds for exact copies, slightly modified text, and completely distinct text.
3. **`CommentModerationTest.java`** (`novel-front`):
   - Verify that clean comments get `audit_status = 1`, sensitive comments get `audit_status = 0`.
   - Test `BookCommentMapper.listCommentByPage` to confirm that comments with `audit_status != 1` are excluded from reader views.
4. **`AgeRatingRestrictionTest.java`** (`novel-front`):
   - Test age restriction logic for `0+`, `13+`, `16+`, and `18+` books with users of varying ages and unauthenticated guest sessions.
5. **`DuplicateChapterDetectionTest.java`** (`novel-front`):
   - Test exact SHA-256 duplicate blocking.
   - Test SimHash plagiarism detection flagging chapters for manual moderation.
6. **`CopyrightReportWorkflowTest.java`** (`novel-admin` & `novel-front`):
   - Integration test: Report submission -> Admin Takedown -> Reader Portal content hiding -> Author Appeal submission -> Admin Appeal approval -> Content Restoration.
7. **`ChapterRevisionHistoryTest.java`** (`novel-front`):
   - Verify version snapshot creation on chapter edit and check side-by-side diff output.
8. **`OwnershipProofUploadTest.java`** (`novel-front` & `novel-admin`):
   - Test file upload, SHA-256 file hashing, and admin verification status updates.

---

## 6. Summary Matrix of Gaps & Solutions

| Feature Category | Existing Code / State | Identified Gap / Defect | Required Solution for 100% Compliance |
|---|---|---|---|
| **Comment Moderation** | `audit_status` in DB | `listCommentByPage` ignores `audit_status`, displaying pending comments | Fix Mapper XML filter + add DFA SensitiveWordFilter & Admin batch review UI |
| **Novel & Chapter Moderation** | `book.status` (0 stock, 1 list) | No `audit_status` on `book_index` or audit queue for novels/chapters | Add `audit_status` to `book` & `book_index`, build Moderation Queue in admin |
| **Image Moderation** | Basic extension check | No image queue, hash check, or NSFW filtering | Add image hash checking, upload moderation status, and admin image review queue |
| **Age Rating & Restrictions** | `work_direction` only | No age rating (13+, 16+, 18+) or user DOB age restriction checks | Add `age_rating` to `book`, `date_of_birth` to `user`, and enforce access control in controllers |
| **Anti-Spam & Rate Limit** | None | Users can flood comments & chapter submissions | Implement Redis `@RateLimit` on comment and chapter creation endpoints |
| **Duplicate & Plagiarism** | Unique `(book_name, author_name)` | No text similarity or chapter plagiarism detection | Implement SHA-256 exact check + 64-bit SimHash similarity engine |
| **Copyright Intake** | Generic `user_feedback` | No dedicated DMCA / copyright report schema or workflow | Create `copyright_report` table, front-end report form, and admin review dashboard |
| **Takedown & Appeal** | Manual delete | No formal takedown state, notice, or author appeal workflow | Add `TAKEN_DOWN` status, `copyright_appeal` table, author notice & appeal audit workflow |
| **Revision History** | In-place overwrite | Old chapter content lost on edit | Create `book_content_history` versioning table & revision history diff viewer |
| **Ownership Evidence** | KYC for payouts only | No novel copyright proof or manuscript evidence storage | Create `book_ownership_proof` table, author upload interface, and admin verification |

