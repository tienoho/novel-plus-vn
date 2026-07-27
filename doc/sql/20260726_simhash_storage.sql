-- Đồng bộ kiểu lưu SimHash với model Java: chuỗi nhị phân 64 ký tự.
-- Migration giữ nguyên giá trị BIGINT cũ bằng cách chuyển sang biểu diễn bit 64-bit.

DROP PROCEDURE IF EXISTS `migrate_simhash_storage`;
DELIMITER $$
CREATE PROCEDURE `migrate_simhash_storage`()
BEGIN
  IF NOT EXISTS (
    SELECT 1
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'book_index'
      AND COLUMN_NAME = 'sim_hash'
  ) THEN
    ALTER TABLE `book_index`
      ADD COLUMN `sim_hash` CHAR(64) DEFAULT NULL
      COMMENT 'SimHash 64-bit ở dạng chuỗi nhị phân';
  ELSE
    ALTER TABLE `book_index`
      MODIFY COLUMN `sim_hash` CHAR(64) DEFAULT NULL
      COMMENT 'SimHash 64-bit ở dạng chuỗi nhị phân';

    UPDATE `book_index`
    SET `sim_hash` = LPAD(BIN(CAST(`sim_hash` AS SIGNED)), 64, '0')
    WHERE `sim_hash` IS NOT NULL
      AND `sim_hash` REGEXP '^-?[0-9]+$'
      AND CHAR_LENGTH(`sim_hash`) < 64;
  END IF;
END$$
DELIMITER ;

CALL `migrate_simhash_storage`();
DROP PROCEDURE `migrate_simhash_storage`;
