-- Migration: Moderation, Content Filtering, Age Rating & Copyright Management
SET NAMES utf8mb4;

-- 1-3. Bổ sung cột/index theo cách chạy lặp an toàn.
DROP PROCEDURE IF EXISTS `migrate_moderation_columns`;
DELIMITER $$
CREATE PROCEDURE `migrate_moderation_columns`()
BEGIN
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'book' AND COLUMN_NAME = 'age_rating') THEN
    ALTER TABLE `book` ADD COLUMN `age_rating` TINYINT NOT NULL DEFAULT 0
      COMMENT 'Giới hạn độ tuổi: 0: Tất cả, 13: 13+, 16: 16+, 18: 18+';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'book' AND COLUMN_NAME = 'audit_status') THEN
    ALTER TABLE `book` ADD COLUMN `audit_status` TINYINT NOT NULL DEFAULT 1
      COMMENT 'Trạng thái kiểm duyệt: 0: Chờ duyệt, 1: Đã duyệt, 2: Từ chối, 3: Gỡ bài';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'book' AND COLUMN_NAME = 'audit_reason') THEN
    ALTER TABLE `book` ADD COLUMN `audit_reason` VARCHAR(500) DEFAULT NULL
      COMMENT 'Lý do kiểm duyệt / gỡ bài';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'book' AND INDEX_NAME = 'idx_book_age_audit') THEN
    ALTER TABLE `book` ADD INDEX `idx_book_age_audit` (`age_rating`, `audit_status`);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'book_index' AND COLUMN_NAME = 'audit_status') THEN
    ALTER TABLE `book_index` ADD COLUMN `audit_status` TINYINT NOT NULL DEFAULT 1
      COMMENT 'Trạng thái kiểm duyệt: 0: Chờ duyệt, 1: Đã duyệt, 2: Từ chối, 3: Gỡ bài';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'book_index' AND COLUMN_NAME = 'content_hash') THEN
    ALTER TABLE `book_index` ADD COLUMN `content_hash` CHAR(64) DEFAULT NULL
      COMMENT 'Mã băm SHA-256 nội dung chương';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'book_index' AND COLUMN_NAME = 'sim_hash') THEN
    ALTER TABLE `book_index` ADD COLUMN `sim_hash` BIGINT DEFAULT NULL
      COMMENT 'SimHash 64-bit fingerprint cho lọc trùng';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'book_index' AND INDEX_NAME = 'idx_index_audit') THEN
    ALTER TABLE `book_index` ADD INDEX `idx_index_audit` (`audit_status`);
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'book_index' AND INDEX_NAME = 'idx_content_hash') THEN
    ALTER TABLE `book_index` ADD INDEX `idx_content_hash` (`content_hash`);
  END IF;

  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'user' AND COLUMN_NAME = 'date_of_birth') THEN
    ALTER TABLE `user` ADD COLUMN `date_of_birth` DATE DEFAULT NULL COMMENT 'Ngày sinh người dùng';
  END IF;
  IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE()
                 AND TABLE_NAME = 'user' AND COLUMN_NAME = 'is_age_verified') THEN
    ALTER TABLE `user` ADD COLUMN `is_age_verified` TINYINT NOT NULL DEFAULT 0
      COMMENT 'Đã xác minh độ tuổi: 0: Chưa, 1: Đã xác minh';
  END IF;
END$$
DELIMITER ;

CALL `migrate_moderation_columns`();
DROP PROCEDURE `migrate_moderation_columns`;

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
