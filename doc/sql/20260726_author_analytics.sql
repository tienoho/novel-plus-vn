SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `reader_chapter_event` (
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `client_event_id`   VARCHAR(96)  NOT NULL,
    `reader_key_hash`   CHAR(64)     NOT NULL,
    `book_id`           BIGINT       NOT NULL,
    `index_id`          BIGINT       NOT NULL,
    `event_type`        VARCHAR(16)  NOT NULL,
    `progress_percent`  TINYINT UNSIGNED NOT NULL DEFAULT 0,
    `duration_seconds`  INT UNSIGNED NOT NULL DEFAULT 0,
    `create_time`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reader_event_client` (`client_event_id`),
    KEY `idx_reader_event_book_time` (`book_id`, `create_time`),
    KEY `idx_reader_event_chapter_time` (`index_id`, `create_time`),
    KEY `idx_reader_event_retention` (`book_id`, `reader_key_hash`, `index_id`, `event_type`),
    CONSTRAINT `chk_reader_event_type`
        CHECK (`event_type` IN ('START', 'PROGRESS', 'COMPLETE')),
    CONSTRAINT `chk_reader_event_progress`
        CHECK (`progress_percent` BETWEEN 0 AND 100),
    CONSTRAINT `chk_reader_event_duration`
        CHECK (`duration_seconds` BETWEEN 0 AND 86400)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Sự kiện đọc chương đã ẩn danh, append-only và idempotent';

DROP TRIGGER IF EXISTS `trg_reader_chapter_event_no_update`;
DELIMITER //
CREATE TRIGGER `trg_reader_chapter_event_no_update`
    BEFORE UPDATE ON `reader_chapter_event`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reader_chapter_event is immutable';
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_reader_chapter_event_no_delete`;
DELIMITER //
CREATE TRIGGER `trg_reader_chapter_event_no_delete`
    BEFORE DELETE ON `reader_chapter_event`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reader_chapter_event is immutable';
END//
DELIMITER ;
