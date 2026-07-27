-- P1: đồng tác giả, biên tập viên và phân quyền theo từng tác phẩm.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `author_book_collaborator` (
    `id`                         bigint(20)  NOT NULL AUTO_INCREMENT,
    `book_id`                    bigint(20)  NOT NULL,
    `owner_author_id`            bigint(20)  NOT NULL,
    `collaborator_author_id`     bigint(20)  NOT NULL,
    `role`                       varchar(16) NOT NULL,
    `can_edit_book`              tinyint     NOT NULL DEFAULT 0,
    `can_publish_book`           tinyint     NOT NULL DEFAULT 0,
    `can_manage_chapters`        tinyint     NOT NULL DEFAULT 0,
    `can_publish_chapters`       tinyint     NOT NULL DEFAULT 0,
    `can_manage_story`           tinyint     NOT NULL DEFAULT 0,
    `can_view_analytics`         tinyint     NOT NULL DEFAULT 0,
    `version`                    bigint(20)  NOT NULL DEFAULT 0,
    `created_by_author_id`       bigint(20)  NOT NULL,
    `updated_by_author_id`       bigint(20)  NOT NULL,
    `create_time`                datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`                datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                                ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_author_book_collaborator` (`book_id`, `collaborator_author_id`),
    KEY `idx_author_collaborator_books` (`collaborator_author_id`, `book_id`),
    KEY `idx_author_collaborator_owner` (`owner_author_id`, `book_id`),
    CONSTRAINT `chk_author_collaborator_role`
        CHECK (`role` IN ('CO_AUTHOR', 'EDITOR')),
    CONSTRAINT `chk_author_collaborator_distinct`
        CHECK (`owner_author_id` <> `collaborator_author_id`),
    CONSTRAINT `chk_author_collaborator_version` CHECK (`version` >= 0),
    CONSTRAINT `chk_author_collaborator_edit_book` CHECK (`can_edit_book` IN (0, 1)),
    CONSTRAINT `chk_author_collaborator_publish_book` CHECK (`can_publish_book` IN (0, 1)),
    CONSTRAINT `chk_author_collaborator_manage_chapters` CHECK (`can_manage_chapters` IN (0, 1)),
    CONSTRAINT `chk_author_collaborator_publish_chapters` CHECK (`can_publish_chapters` IN (0, 1)),
    CONSTRAINT `chk_author_collaborator_manage_story` CHECK (`can_manage_story` IN (0, 1)),
    CONSTRAINT `chk_author_collaborator_view_analytics` CHECK (`can_view_analytics` IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Vai trò và quyền cộng tác theo tác phẩm';

CREATE TABLE IF NOT EXISTS `author_book_collaborator_audit` (
    `id`                         bigint(20)  NOT NULL AUTO_INCREMENT,
    `collaborator_record_id`     bigint(20)  NOT NULL,
    `book_id`                    bigint(20)  NOT NULL,
    `owner_author_id`            bigint(20)  NOT NULL,
    `collaborator_author_id`     bigint(20)  NOT NULL,
    `event_type`                 varchar(16) NOT NULL,
    `role`                       varchar(16) NOT NULL,
    `can_edit_book`              tinyint     NOT NULL,
    `can_publish_book`           tinyint     NOT NULL,
    `can_manage_chapters`        tinyint     NOT NULL,
    `can_publish_chapters`       tinyint     NOT NULL,
    `can_manage_story`           tinyint     NOT NULL,
    `can_view_analytics`         tinyint     NOT NULL,
    `version`                    bigint(20)  NOT NULL,
    `actor_author_id`            bigint(20)  NOT NULL,
    `create_time`                datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_author_collaborator_audit_book` (`book_id`, `create_time`, `id`),
    KEY `idx_author_collaborator_audit_record` (`collaborator_record_id`, `create_time`, `id`),
    CONSTRAINT `chk_author_collaborator_audit_event`
        CHECK (`event_type` IN ('ADDED', 'UPDATED', 'REMOVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Audit bất biến cho thay đổi quyền cộng tác';

DROP TRIGGER IF EXISTS `trg_author_book_collaborator_audit_no_update`;
DROP TRIGGER IF EXISTS `trg_author_book_collaborator_audit_no_delete`;
DELIMITER $$
CREATE TRIGGER `trg_author_book_collaborator_audit_no_update`
    BEFORE UPDATE ON `author_book_collaborator_audit`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'author_book_collaborator_audit is immutable';
END$$

CREATE TRIGGER `trg_author_book_collaborator_audit_no_delete`
    BEFORE DELETE ON `author_book_collaborator_audit`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'author_book_collaborator_audit is immutable';
END$$
DELIMITER ;
