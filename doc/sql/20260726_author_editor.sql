-- P1: bản nháp, tự lưu và lịch xuất bản chương cho khu vực tác giả.

CREATE TABLE IF NOT EXISTS `author_chapter_draft` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `draft_no` VARCHAR(64) NOT NULL COMMENT 'Mã bản nháp ổn định dùng trong audit',
  `client_key` VARCHAR(64) NOT NULL COMMENT 'Khóa idempotency do trình soạn thảo tạo',
  `author_id` BIGINT(20) NOT NULL,
  `book_id` BIGINT(20) NOT NULL,
  `index_id` BIGINT(20) DEFAULT NULL COMMENT 'Chương hiện có nếu đây là bản nháp chỉnh sửa',
  `index_name` VARCHAR(100) NOT NULL DEFAULT '',
  `content` MEDIUMTEXT NOT NULL,
  `is_vip` TINYINT NOT NULL DEFAULT 0,
  `status` VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
  `version` BIGINT(20) NOT NULL DEFAULT 0,
  `scheduled_at` DATETIME(3) DEFAULT NULL,
  `published_index_id` BIGINT(20) DEFAULT NULL,
  `last_autosave_at` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `last_error` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_author_draft_no` (`draft_no`),
  UNIQUE KEY `uk_author_draft_client` (`author_id`, `client_key`),
  KEY `idx_author_draft_list` (`author_id`, `status`, `update_time`),
  KEY `idx_author_draft_due` (`status`, `scheduled_at`, `id`),
  KEY `idx_author_draft_chapter` (`author_id`, `index_id`),
  CONSTRAINT `chk_author_draft_status` CHECK
    (`status` IN ('DRAFT', 'SCHEDULED', 'PUBLISHING', 'PUBLISHED', 'CANCELLED')),
  CONSTRAINT `chk_author_draft_vip` CHECK (`is_vip` IN (0, 1)),
  CONSTRAINT `chk_author_draft_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Bản nháp chương riêng tư của tác giả';

CREATE TABLE IF NOT EXISTS `author_chapter_draft_event` (
  `id` BIGINT(20) NOT NULL AUTO_INCREMENT,
  `draft_id` BIGINT(20) NOT NULL,
  `draft_no` VARCHAR(64) NOT NULL,
  `event_type` VARCHAR(32) NOT NULL,
  `from_status` VARCHAR(16) DEFAULT NULL,
  `to_status` VARCHAR(16) NOT NULL,
  `actor_type` VARCHAR(16) NOT NULL,
  `actor_id` BIGINT(20) DEFAULT NULL,
  `detail` VARCHAR(500) DEFAULT NULL,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`id`),
  KEY `idx_author_draft_event` (`draft_id`, `create_time`),
  KEY `idx_author_draft_event_no` (`draft_no`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Audit trạng thái bản nháp chương bất biến';

DROP TRIGGER IF EXISTS `trg_author_chapter_draft_event_no_update`;
DROP TRIGGER IF EXISTS `trg_author_chapter_draft_event_no_delete`;
DELIMITER $$
CREATE TRIGGER `trg_author_chapter_draft_event_no_update`
  BEFORE UPDATE ON `author_chapter_draft_event`
  FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'author_chapter_draft_event is immutable';
END$$

CREATE TRIGGER `trg_author_chapter_draft_event_no_delete`
  BEFORE DELETE ON `author_chapter_draft_event`
  FOR EACH ROW
BEGIN
  SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'author_chapter_draft_event is immutable';
END$$
DELIMITER ;
