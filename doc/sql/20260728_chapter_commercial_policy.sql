-- P1: giá chương tùy chỉnh, tự mở khóa và cửa sổ miễn phí có thời hạn.

CREATE TABLE IF NOT EXISTS `chapter_commercial_policy` (
  `book_index_id` BIGINT(20) NOT NULL,
  `book_id` BIGINT(20) NOT NULL,
  `custom_price` INT DEFAULT NULL COMMENT 'Giá Xu do tác giả đặt; NULL dùng giá tự động',
  `unlock_at` DATETIME(3) DEFAULT NULL COMMENT 'Từ thời điểm này chương miễn phí vĩnh viễn',
  `free_from` DATETIME(3) DEFAULT NULL COMMENT 'Bắt đầu sự kiện miễn phí, bao gồm mốc này',
  `free_until` DATETIME(3) DEFAULT NULL COMMENT 'Kết thúc sự kiện miễn phí, không bao gồm mốc này',
  `version` BIGINT(20) NOT NULL DEFAULT 0,
  `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
  `update_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
  PRIMARY KEY (`book_index_id`),
  KEY `idx_chapter_policy_book` (`book_id`, `book_index_id`),
  CONSTRAINT `fk_chapter_policy_index` FOREIGN KEY (`book_index_id`) REFERENCES `book_index` (`id`) ON DELETE CASCADE,
  CONSTRAINT `chk_chapter_policy_price` CHECK (`custom_price` IS NULL OR `custom_price` BETWEEN 1 AND 1000000),
  CONSTRAINT `chk_chapter_policy_window` CHECK
    ((`free_from` IS NULL AND `free_until` IS NULL)
      OR (`free_from` IS NOT NULL AND `free_until` IS NOT NULL AND `free_from` < `free_until`)),
  CONSTRAINT `chk_chapter_policy_version` CHECK (`version` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Chính sách thương mại tùy chọn của chương';

DROP PROCEDURE IF EXISTS `p_add_chapter_commercial_draft_columns`;
DELIMITER $$
CREATE PROCEDURE `p_add_chapter_commercial_draft_columns`()
BEGIN
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'author_chapter_draft' AND column_name = 'book_price'
  ) THEN
    ALTER TABLE `author_chapter_draft` ADD COLUMN `book_price` INT DEFAULT NULL AFTER `is_vip`;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'author_chapter_draft' AND column_name = 'custom_price'
  ) THEN
    ALTER TABLE `author_chapter_draft` ADD COLUMN `custom_price` INT DEFAULT NULL AFTER `book_price`;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'author_chapter_draft' AND column_name = 'unlock_at'
  ) THEN
    ALTER TABLE `author_chapter_draft` ADD COLUMN `unlock_at` DATETIME(3) DEFAULT NULL AFTER `custom_price`;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'author_chapter_draft' AND column_name = 'free_from'
  ) THEN
    ALTER TABLE `author_chapter_draft` ADD COLUMN `free_from` DATETIME(3) DEFAULT NULL AFTER `unlock_at`;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.columns
    WHERE table_schema = DATABASE() AND table_name = 'author_chapter_draft' AND column_name = 'free_until'
  ) THEN
    ALTER TABLE `author_chapter_draft` ADD COLUMN `free_until` DATETIME(3) DEFAULT NULL AFTER `free_from`;
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'author_chapter_draft'
      AND constraint_name = 'chk_author_draft_commercial_price'
  ) THEN
    ALTER TABLE `author_chapter_draft`
      ADD CONSTRAINT `chk_author_draft_commercial_price` CHECK
        (`book_price` IS NULL OR `book_price` >= 0);
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'author_chapter_draft'
      AND constraint_name = 'chk_author_draft_custom_price'
  ) THEN
    ALTER TABLE `author_chapter_draft`
      ADD CONSTRAINT `chk_author_draft_custom_price` CHECK
        (`custom_price` IS NULL OR `custom_price` BETWEEN 1 AND 1000000);
  END IF;
  IF NOT EXISTS (
    SELECT 1 FROM information_schema.table_constraints
    WHERE constraint_schema = DATABASE() AND table_name = 'author_chapter_draft'
      AND constraint_name = 'chk_author_draft_free_window'
  ) THEN
    ALTER TABLE `author_chapter_draft`
      ADD CONSTRAINT `chk_author_draft_free_window` CHECK
        ((`free_from` IS NULL AND `free_until` IS NULL)
          OR (`free_from` IS NOT NULL AND `free_until` IS NOT NULL AND `free_from` < `free_until`));
  END IF;
END$$
DELIMITER ;

CALL `p_add_chapter_commercial_draft_columns`();
DROP PROCEDURE `p_add_chapter_commercial_draft_columns`;

-- Bản nháp cũ không có giá preview; chỉ backfill giá an toàn và không thay chính sách chương đã xuất bản.
UPDATE `author_chapter_draft` d
LEFT JOIN `book_index` bi ON bi.id = d.index_id
SET d.book_price = CASE
  WHEN d.is_vip = 0 THEN 0
  WHEN bi.book_price IS NOT NULL AND bi.book_price > 0 THEN bi.book_price
  ELSE 1
END
WHERE d.book_price IS NULL;
