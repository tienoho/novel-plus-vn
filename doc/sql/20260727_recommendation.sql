SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS `migrate_recommendation_indexes`;
DELIMITER $$
CREATE PROCEDURE `migrate_recommendation_indexes`()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'book'
          AND index_name = 'idx_book_recommendation_pool'
    ) THEN
        ALTER TABLE `book`
            ADD INDEX `idx_book_recommendation_pool`
                (`status`, `audit_status`, `cover_audit_status`, `age_rating`, `cat_id`);
    END IF;

    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'user_buy_record'
          AND index_name = 'idx_user_buy_recommendation'
    ) THEN
        ALTER TABLE `user_buy_record`
            ADD INDEX `idx_user_buy_recommendation` (`user_id`, `book_id`);
    END IF;
END$$
DELIMITER ;

CALL `migrate_recommendation_indexes`();
DROP PROCEDURE `migrate_recommendation_indexes`;
