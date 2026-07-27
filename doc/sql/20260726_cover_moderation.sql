SET NAMES utf8mb4;

-- Bìa đã tồn tại trước migration được coi là đã duyệt để không làm ngập hàng đợi.
-- Mọi bìa được tạo hoặc thay mới sau migration phải được ứng dụng đặt lại về PENDING (0).
DROP PROCEDURE IF EXISTS `migrate_cover_moderation`;
DELIMITER //
CREATE PROCEDURE `migrate_cover_moderation`()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'book'
          AND COLUMN_NAME = 'cover_audit_status'
    ) THEN
        ALTER TABLE `book`
            ADD COLUMN `cover_audit_status` TINYINT NOT NULL DEFAULT 1
                COMMENT '0: chờ duyệt, 1: đã duyệt, 2: từ chối'
                AFTER `pic_url`;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'book'
          AND COLUMN_NAME = 'cover_audit_reason'
    ) THEN
        ALTER TABLE `book`
            ADD COLUMN `cover_audit_reason` VARCHAR(500) DEFAULT NULL
                COMMENT 'Lý do từ chối ảnh bìa'
                AFTER `cover_audit_status`;
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.STATISTICS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'book'
          AND INDEX_NAME = 'idx_book_cover_audit'
    ) THEN
        ALTER TABLE `book`
            ADD INDEX `idx_book_cover_audit` (`cover_audit_status`, `update_time`);
    END IF;
END//
DELIMITER ;

CALL `migrate_cover_moderation`();
DROP PROCEDURE IF EXISTS `migrate_cover_moderation`;
