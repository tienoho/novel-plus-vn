-- P1: provenance bất biến cho nội dung do AI hỗ trợ, không lưu bản thảo hoặc đầu ra dạng rõ.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `author_ai_usage` (
    `id`              bigint(20)   NOT NULL AUTO_INCREMENT,
    `book_id`         bigint(20)   NOT NULL,
    `owner_author_id` bigint(20)   NOT NULL,
    `actor_author_id` bigint(20)   NOT NULL,
    `draft_id`        bigint(20)   DEFAULT NULL,
    `operation`       varchar(16)  NOT NULL,
    `model_name`      varchar(100) NOT NULL,
    `input_sha256`    char(64)     NOT NULL COMMENT 'Hash đầu vào; không lưu nội dung bản thảo thô',
    `output_sha256`   char(64)     NOT NULL COMMENT 'Hash đầu ra; không lưu nội dung AI thô',
    `input_chars`     int          NOT NULL,
    `output_chars`    int          NOT NULL,
    `context_items`   int          NOT NULL DEFAULT 0,
    `create_time`     datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_author_ai_usage_book_time` (`book_id`, `create_time`, `id`),
    KEY `idx_author_ai_usage_actor_time` (`actor_author_id`, `create_time`, `id`),
    KEY `idx_author_ai_usage_draft` (`draft_id`, `id`),
    CONSTRAINT `chk_author_ai_operation`
        CHECK (`operation` IN ('EXPAND', 'CONDENSE', 'CONTINUE', 'POLISH')),
    CONSTRAINT `chk_author_ai_input_hash` CHECK (CHAR_LENGTH(`input_sha256`) = 64),
    CONSTRAINT `chk_author_ai_output_hash` CHECK (CHAR_LENGTH(`output_sha256`) = 64),
    CONSTRAINT `chk_author_ai_lengths` CHECK (`input_chars` > 0 AND `output_chars` > 0),
    CONSTRAINT `chk_author_ai_context_items` CHECK (`context_items` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Provenance bất biến cho thao tác AI của tác giả; chỉ lưu hash và metadata';

DROP TRIGGER IF EXISTS `trg_author_ai_usage_no_update`;
DROP TRIGGER IF EXISTS `trg_author_ai_usage_no_delete`;
DELIMITER $$
CREATE TRIGGER `trg_author_ai_usage_no_update`
    BEFORE UPDATE ON `author_ai_usage`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'author_ai_usage is immutable';
END$$

CREATE TRIGGER `trg_author_ai_usage_no_delete`
    BEFORE DELETE ON `author_ai_usage`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'author_ai_usage is immutable';
END$$
DELIMITER ;
