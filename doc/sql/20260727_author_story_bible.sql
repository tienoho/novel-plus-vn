-- Kho tư liệu riêng tư của tác giả: dàn ý, nhân vật, địa điểm và dòng thời gian.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `author_story_item` (
    `id`             bigint(20)   NOT NULL AUTO_INCREMENT,
    `author_id`      bigint(20)   NOT NULL COMMENT 'Tác giả sở hữu dữ liệu',
    `book_id`        bigint(20)   NOT NULL COMMENT 'Tác phẩm liên quan',
    `item_type`      varchar(16)  NOT NULL COMMENT 'OUTLINE, CHARACTER, LOCATION hoặc TIMELINE',
    `title`          varchar(120) NOT NULL,
    `content`        mediumtext   NOT NULL,
    `timeline_label` varchar(100) DEFAULT NULL COMMENT 'Mốc thời gian trong thế giới truyện, không bắt buộc là ngày thật',
    `sort_order`     int          NOT NULL DEFAULT 0,
    `version`        bigint(20)   NOT NULL DEFAULT 0 COMMENT 'Optimistic version chống ghi đè giữa nhiều tab',
    `create_time`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_author_story_book_type_order` (`author_id`, `book_id`, `item_type`, `sort_order`, `id`),
    CONSTRAINT `chk_author_story_item_type`
        CHECK (`item_type` IN ('OUTLINE', 'CHARACTER', 'LOCATION', 'TIMELINE')),
    CONSTRAINT `chk_author_story_sort_order` CHECK (`sort_order` >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Kho tư liệu riêng tư theo tác phẩm của tác giả';
