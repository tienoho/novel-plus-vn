-- Campaign, mã quà và biên nhận đổi quà. Code chỉ lưu HMAC-SHA256, không lưu plaintext.
-- Reward đi qua ledger Xu hoặc Vé đọc hiện hữu trong cùng transaction với redemption.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `gift_campaign`
(
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
    `campaign_code`        VARCHAR(32)  NOT NULL,
    `campaign_name`        VARCHAR(100) NOT NULL,
    `reward_type`          VARCHAR(24)  NOT NULL,
    `reward_amount`        BIGINT       NOT NULL,
    `ticket_validity_days` INT                   DEFAULT NULL,
    `start_at`             DATETIME(3)  NOT NULL,
    `end_at`               DATETIME(3)  NOT NULL,
    `max_redemptions`      BIGINT       NOT NULL,
    `redeemed_count`       BIGINT       NOT NULL DEFAULT 0,
    `max_per_user`         INT          NOT NULL DEFAULT 1,
    `status`               VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    `policy_version`       VARCHAR(32)  NOT NULL,
    `version`              BIGINT       NOT NULL DEFAULT 0,
    `create_time`          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
                                           ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gift_campaign_code` (`campaign_code`),
    KEY `idx_gift_campaign_status_window` (`status`, `start_at`, `end_at`, `id`),
    CONSTRAINT `chk_gift_campaign_reward_type` CHECK
        (`reward_type` IN ('XU', 'READING_TICKET')),
    CONSTRAINT `chk_gift_campaign_reward_amount` CHECK
        (`reward_amount` BETWEEN 1 AND 100000),
    CONSTRAINT `chk_gift_campaign_ticket_validity` CHECK
        ((`reward_type` = 'READING_TICKET' AND `ticket_validity_days` BETWEEN 1 AND 366)
         OR (`reward_type` = 'XU' AND `ticket_validity_days` IS NULL)),
    CONSTRAINT `chk_gift_campaign_window` CHECK (`end_at` > `start_at`),
    CONSTRAINT `chk_gift_campaign_limit` CHECK
        (`max_redemptions` > 0 AND `redeemed_count` BETWEEN 0 AND `max_redemptions`
         AND `max_per_user` BETWEEN 1 AND 100),
    CONSTRAINT `chk_gift_campaign_status` CHECK (`status` IN ('DRAFT', 'ACTIVE', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Chiến dịch mã quà cấp Xu hoặc Vé đọc qua ledger hiện hữu';

CREATE TABLE IF NOT EXISTS `gift_code`
(
    `id`              BIGINT      NOT NULL AUTO_INCREMENT,
    `campaign_id`     BIGINT      NOT NULL,
    `code_hash`       CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    `code_hint`       VARCHAR(12) NOT NULL,
    `max_redemptions` BIGINT      NOT NULL DEFAULT 1,
    `redeemed_count`  BIGINT      NOT NULL DEFAULT 0,
    `status`          VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    `version`         BIGINT      NOT NULL DEFAULT 0,
    `create_time`     DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gift_code_hash` (`code_hash`),
    KEY `idx_gift_code_campaign` (`campaign_id`, `status`, `id`),
    CONSTRAINT `chk_gift_code_limit` CHECK
        (`max_redemptions` > 0 AND `redeemed_count` BETWEEN 0 AND `max_redemptions`),
    CONSTRAINT `chk_gift_code_status` CHECK (`status` IN ('ACTIVE', 'EXHAUSTED', 'REVOKED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Mã quà chỉ lưu HMAC; code_hint không đủ để đổi quà';

CREATE TABLE IF NOT EXISTS `gift_redemption`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `campaign_id`        BIGINT       NOT NULL,
    `code_id`            BIGINT       NOT NULL,
    `user_id`            BIGINT       NOT NULL,
    `reward_type`        VARCHAR(24)  NOT NULL,
    `reward_amount`      BIGINT       NOT NULL,
    `reward_ledger_type` VARCHAR(24)  NOT NULL,
    `reward_ledger_id`   BIGINT       NOT NULL,
    `idempotency_key`    VARCHAR(128) NOT NULL,
    `client_request_id`  VARCHAR(64)  NOT NULL,
    `redeemed_at`        DATETIME(3)  NOT NULL,
    `policy_version`     VARCHAR(32)  NOT NULL,
    `create_time`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gift_redemption_code_user` (`code_id`, `user_id`),
    UNIQUE KEY `uk_gift_redemption_request` (`user_id`, `client_request_id`),
    UNIQUE KEY `uk_gift_redemption_idempotency` (`idempotency_key`),
    UNIQUE KEY `uk_gift_redemption_ledger`
        (`reward_ledger_type`, `reward_ledger_id`),
    KEY `idx_gift_redemption_campaign_user` (`campaign_id`, `user_id`, `id`),
    CONSTRAINT `chk_gift_redemption_reward_type` CHECK
        (`reward_type` IN ('XU', 'READING_TICKET')),
    CONSTRAINT `chk_gift_redemption_ledger_type` CHECK
        (`reward_ledger_type` IN ('WALLET_XU', 'READING_TICKET')),
    CONSTRAINT `chk_gift_redemption_amount` CHECK (`reward_amount` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Biên nhận đổi mã quà bất biến, liên kết đúng một ledger reward';

DROP TRIGGER IF EXISTS `trg_gift_redemption_no_update`;
DROP TRIGGER IF EXISTS `trg_gift_redemption_no_delete`;

DELIMITER $$
CREATE TRIGGER `trg_gift_redemption_no_update`
    BEFORE UPDATE ON `gift_redemption`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gift_redemption is immutable';
END$$

CREATE TRIGGER `trg_gift_redemption_no_delete`
    BEFORE DELETE ON `gift_redemption`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gift_redemption is immutable';
END$$
DELIMITER ;

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT seed.parent_id, 'Mã quà', 'novel/giftCode', 'novel:giftCode:view',
       1, 'fa fa-gift', 10, NOW()
FROM (
    SELECT COALESCE((
        SELECT parent_id FROM `sys_menu` WHERE perms = 'novel:author:author' LIMIT 1
    ), 0) AS parent_id
) seed
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:giftCode:view');

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Cấu hình chiến dịch mã quà', NULL,
       'novel:giftCode:config', 2, NULL, 1, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:giftCode:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:giftCode:config');

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Phát hành mã quà', NULL,
       'novel:giftCode:issue', 2, NULL, 2, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:giftCode:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:giftCode:issue');

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT role.role_id, menu.menu_id
FROM `sys_role` role
JOIN `sys_menu` menu
  ON menu.perms IN ('novel:giftCode:view', 'novel:giftCode:config', 'novel:giftCode:issue')
WHERE role.role_sign = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` relation
      WHERE relation.role_id=role.role_id AND relation.menu_id=menu.menu_id
  );
