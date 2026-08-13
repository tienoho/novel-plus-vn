-- Luật chơi công khai dạng plain text, version hóa và do admin phát hành.

CREATE TABLE `gamification_public_policy`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `policy_version` VARCHAR(32)  NOT NULL,
    `title`          VARCHAR(160) NOT NULL,
    `content_text`   TEXT         NOT NULL,
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    `published_by`   BIGINT                DEFAULT NULL,
    `published_at`   DATETIME(3)           DEFAULT NULL,
    `archived_at`    DATETIME(3)           DEFAULT NULL,
    `version`        BIGINT       NOT NULL DEFAULT 0,
    `published_slot` TINYINT GENERATED ALWAYS AS
        (CASE WHEN `status` = 'PUBLISHED' THEN 1 ELSE NULL END) STORED,
    `create_time`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gamification_public_policy_version` (`policy_version`),
    UNIQUE KEY `uk_gamification_public_policy_published` (`published_slot`),
    CONSTRAINT `chk_gamification_public_policy_status` CHECK (`status` IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT `chk_gamification_public_policy_publish` CHECK
        ((`status` = 'DRAFT' AND `published_by` IS NULL AND `published_at` IS NULL AND `archived_at` IS NULL)
         OR (`status` = 'PUBLISHED' AND `published_by` IS NOT NULL
             AND `published_at` IS NOT NULL AND `archived_at` IS NULL)
         OR (`status` = 'ARCHIVED' AND `published_by` IS NOT NULL
             AND `published_at` IS NOT NULL AND `archived_at` IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Luật chơi gamification công khai theo phiên bản';

CREATE TABLE `gamification_public_policy_audit`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `policy_id`   BIGINT      NOT NULL,
    `event_type`  VARCHAR(24) NOT NULL,
    `from_status` VARCHAR(16)          DEFAULT NULL,
    `to_status`   VARCHAR(16) NOT NULL,
    `operator_id` BIGINT      NOT NULL,
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_gamification_policy_audit` (`policy_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Audit bất biến khi tạo/phát hành luật chơi';

INSERT INTO `gamification_public_policy`
    (`policy_version`, `title`, `content_text`, `status`, `published_by`, `published_at`)
VALUES
    ('v1', 'Luật chơi Ngọn Đuốc và xếp hạng',
     CONCAT(
       '1. Ngọn Đuốc là điểm hoạt động, không phải tiền, không đổi thành Xu và không chuyển nhượng.\n\n',
       '2. Mỗi lô Đuốc có thời hạn. Hệ thống ưu tiên dùng lô sắp hết hạn trước; thời hạn cụ thể hiển thị trong tài khoản.\n\n',
       '3. Người đọc phải chọn kỳ xếp hạng trước khi thắp Đuốc. Kỳ tháng và kỳ đặc biệt có bộ đếm, snapshot và kết quả thưởng độc lập.\n\n',
       '4. Xếp hạng ưu tiên tổng Đuốc, số người thắp riêng biệt, thời điểm đạt kết quả và mã truyện. Kết quả chỉ chính thức sau bước rà soát.\n\n',
       '5. Thưởng tác giả chỉ được cấp sau khi kỳ đã chốt và qua đối soát. Mọi quyết định tài chính vẫn tuân theo KYC, clearing và quy trình bốn mắt.\n\n',
       '6. Hệ thống dùng device ID ẩn danh và hash IP để phát hiện lạm dụng; không lưu IP hoặc device ID dạng thô. Quyết định chặn chỉ áp dụng khi có rule được cấu hình.\n\n',
       'Nội dung này mô tả luật chơi sản phẩm và không thay thế điều khoản sử dụng hoặc thông báo pháp lý.'),
     'PUBLISHED', 1, NOW());

INSERT INTO `gamification_public_policy_audit`
    (`policy_id`, `event_type`, `from_status`, `to_status`, `operator_id`)
SELECT `id`, 'PUBLISHED', NULL, 'PUBLISHED', 1
FROM `gamification_public_policy` WHERE `policy_version` = 'v1';

DELIMITER //
CREATE TRIGGER `trg_gamification_public_policy_guard`
    BEFORE UPDATE ON `gamification_public_policy` FOR EACH ROW
BEGIN
    IF OLD.`status` <> 'DRAFT'
        AND (NOT (NEW.`policy_version` <=> OLD.`policy_version`)
             OR NOT (NEW.`title` <=> OLD.`title`)
             OR NOT (NEW.`content_text` <=> OLD.`content_text`)
             OR NOT (NEW.`published_by` <=> OLD.`published_by`)
             OR NOT (NEW.`published_at` <=> OLD.`published_at`)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'published gamification policy content is immutable';
    END IF;
    IF NOT ((OLD.`status` = 'DRAFT' AND NEW.`status` = 'PUBLISHED')
        OR (OLD.`status` = 'PUBLISHED' AND NEW.`status` = 'ARCHIVED')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'invalid gamification policy status transition';
    END IF;
END//
CREATE TRIGGER `trg_gamification_public_policy_no_delete`
    BEFORE DELETE ON `gamification_public_policy` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification_public_policy cannot be deleted';
END//
CREATE TRIGGER `trg_gamification_public_policy_audit_no_update`
    BEFORE UPDATE ON `gamification_public_policy_audit` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification_public_policy_audit is immutable';
END//
CREATE TRIGGER `trg_gamification_public_policy_audit_no_delete`
    BEFORE DELETE ON `gamification_public_policy_audit` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification_public_policy_audit is immutable';
END//
DELIMITER ;
