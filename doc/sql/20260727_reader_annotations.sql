-- Đồng bộ vị trí đọc, đánh dấu đoạn và ghi chú riêng tư của độc giả.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `reader_progress` (
    `user_id`          bigint(20)    NOT NULL,
    `book_id`          bigint(20)    NOT NULL,
    `book_index_id`    bigint(20)    NOT NULL,
    `paragraph_index`  int           NOT NULL DEFAULT 0,
    `character_offset` int           NOT NULL DEFAULT 0,
    `progress_percent` decimal(5,2)  NOT NULL DEFAULT 0.00,
    `version`          bigint(20)    NOT NULL DEFAULT 0,
    `create_time`      datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`      datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`user_id`, `book_id`),
    KEY `idx_reader_progress_chapter` (`user_id`, `book_index_id`),
    CONSTRAINT `chk_reader_progress_paragraph` CHECK (`paragraph_index` >= 0),
    CONSTRAINT `chk_reader_progress_offset` CHECK (`character_offset` >= 0),
    CONSTRAINT `chk_reader_progress_percent` CHECK (`progress_percent` >= 0 AND `progress_percent` <= 100)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Vị trí đọc gần nhất của người dùng theo tác phẩm';

CREATE TABLE IF NOT EXISTS `reader_annotation` (
    `id`               bigint(20)    NOT NULL AUTO_INCREMENT,
    `user_id`          bigint(20)    NOT NULL,
    `book_id`          bigint(20)    NOT NULL,
    `book_index_id`    bigint(20)    NOT NULL,
    `annotation_type`  varchar(16)   NOT NULL COMMENT 'BOOKMARK hoặc NOTE',
    `paragraph_index`  int           NOT NULL DEFAULT 0,
    `character_offset` int           NOT NULL DEFAULT 0,
    `selected_text`    varchar(500)  DEFAULT NULL,
    `note_text`        varchar(2000) DEFAULT NULL,
    `version`          bigint(20)    NOT NULL DEFAULT 0,
    `create_time`      datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`      datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_reader_annotation_chapter` (`user_id`, `book_id`, `book_index_id`, `character_offset`, `id`),
    CONSTRAINT `chk_reader_annotation_type` CHECK (`annotation_type` IN ('BOOKMARK', 'NOTE')),
    CONSTRAINT `chk_reader_annotation_paragraph` CHECK (`paragraph_index` >= 0),
    CONSTRAINT `chk_reader_annotation_offset` CHECK (`character_offset` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Đánh dấu đoạn và ghi chú riêng tư của độc giả';
