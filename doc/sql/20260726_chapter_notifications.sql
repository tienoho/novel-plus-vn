-- Theo dõi tác giả và outbox thông báo chương mới.
-- Không backfill chapter_publish_event để tránh gửi thông báo cho chương cũ khi nâng cấp.

CREATE TABLE IF NOT EXISTS `user_author_follow` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `author_id` bigint NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_author_follow` (`user_id`, `author_id`),
    KEY `idx_author_follow_author` (`author_id`, `user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Tác giả được độc giả theo dõi';

CREATE TABLE IF NOT EXISTS `chapter_publish_event` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `chapter_id` bigint NOT NULL,
    `book_id` bigint NOT NULL,
    `author_id` bigint DEFAULT NULL,
    `book_name` varchar(50) NOT NULL,
    `chapter_name` varchar(100) NOT NULL,
    `status` varchar(16) NOT NULL DEFAULT 'PENDING',
    `attempts` int NOT NULL DEFAULT 0,
    `next_attempt_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `processed_time` datetime DEFAULT NULL,
    `last_error` varchar(500) DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chapter_publish_event_chapter` (`chapter_id`),
    KEY `idx_chapter_publish_event_pending` (`status`, `next_attempt_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Outbox chương lần đầu đạt trạng thái đã duyệt';

CREATE TABLE IF NOT EXISTS `user_notification` (
    `id` bigint NOT NULL AUTO_INCREMENT,
    `user_id` bigint NOT NULL,
    `notification_type` varchar(32) NOT NULL,
    `book_id` bigint DEFAULT NULL,
    `author_id` bigint DEFAULT NULL,
    `chapter_id` bigint DEFAULT NULL,
    `book_name` varchar(50) DEFAULT NULL,
    `chapter_name` varchar(100) DEFAULT NULL,
    `is_read` tinyint NOT NULL DEFAULT 0,
    `read_time` datetime DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_notification_reference` (`user_id`, `notification_type`, `chapter_id`),
    KEY `idx_user_notification_inbox` (`user_id`, `is_read`, `id`),
    KEY `idx_user_notification_created` (`user_id`, `create_time`, `id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci
  COMMENT='Hộp thông báo trong ứng dụng';

DROP TRIGGER IF EXISTS `trg_book_index_publish_event_insert`;
DROP TRIGGER IF EXISTS `trg_book_index_publish_event_approve`;

DELIMITER $$
CREATE TRIGGER `trg_book_index_publish_event_insert`
AFTER INSERT ON `book_index`
FOR EACH ROW
BEGIN
    IF NEW.audit_status = 1 THEN
        INSERT IGNORE INTO `chapter_publish_event`
            (`chapter_id`, `book_id`, `author_id`, `book_name`, `chapter_name`)
        SELECT NEW.id, NEW.book_id, b.author_id, b.book_name, NEW.index_name
        FROM `book` b
        WHERE b.id = NEW.book_id
          AND b.audit_status = 1;
    END IF;
END$$

CREATE TRIGGER `trg_book_index_publish_event_approve`
AFTER UPDATE ON `book_index`
FOR EACH ROW
BEGIN
    IF NEW.audit_status = 1 AND OLD.audit_status <> 1 THEN
        INSERT IGNORE INTO `chapter_publish_event`
            (`chapter_id`, `book_id`, `author_id`, `book_name`, `chapter_name`)
        SELECT NEW.id, NEW.book_id, b.author_id, b.book_name, NEW.index_name
        FROM `book` b
        WHERE b.id = NEW.book_id
          AND b.audit_status = 1;
    END IF;
END$$
DELIMITER ;

