-- Hồ sơ KYC, yêu cầu rút thu nhập và audit trail tác giả.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `author_kyc_profile`
(
    `id`                           bigint(20)    NOT NULL AUTO_INCREMENT,
    `author_id`                    bigint(20)    NOT NULL,
    `user_id`                      bigint(20)    NOT NULL,
    `legal_name_ciphertext`        varchar(1024) NOT NULL,
    `date_of_birth_ciphertext`     varchar(512)  NOT NULL,
    `identity_type`                varchar(16)   NOT NULL,
    `identity_number_ciphertext`   varchar(1024) NOT NULL,
    `identity_number_hash`         char(64)      NOT NULL,
    `identity_number_last4`        char(4)       NOT NULL,
    `tax_code_ciphertext`          varchar(1024) DEFAULT NULL,
    `bank_code`                    varchar(20)   NOT NULL,
    `bank_account_ciphertext`      varchar(1024) NOT NULL,
    `bank_account_hash`            char(64)      NOT NULL,
    `bank_account_last4`           char(4)       NOT NULL,
    `bank_account_name_ciphertext` varchar(1024) NOT NULL,
    `consent_version`              varchar(32)   NOT NULL,
    `consented_at`                 datetime(3)   NOT NULL,
    `status`                       varchar(16)   NOT NULL DEFAULT 'PENDING',
    `submission_version`           int           NOT NULL DEFAULT 1,
    `submitted_at`                 datetime(3)   NOT NULL,
    `reviewed_at`                  datetime(3)   DEFAULT NULL,
    `reviewed_by`                  bigint(20)    DEFAULT NULL,
    `rejection_reason`             varchar(500)  DEFAULT NULL,
    `create_time`                  datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`                  datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_author_kyc_author` (`author_id`),
    UNIQUE KEY `uk_author_kyc_identity_hash` (`identity_number_hash`),
    KEY `idx_author_kyc_user` (`user_id`),
    KEY `idx_author_kyc_status_submitted` (`status`, `submitted_at`),
    CONSTRAINT `chk_author_kyc_status` CHECK (`status` IN ('PENDING', 'VERIFIED', 'REJECTED')),
    CONSTRAINT `chk_author_kyc_identity_type` CHECK (`identity_type` IN ('CCCD', 'PASSPORT'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Hồ sơ định danh và ngân hàng đã mã hóa của tác giả';

CREATE TABLE IF NOT EXISTS `author_kyc_audit`
(
    `id`               bigint(20)   NOT NULL AUTO_INCREMENT,
    `kyc_profile_id`   bigint(20)   NOT NULL,
    `author_id`        bigint(20)   NOT NULL,
    `user_id`          bigint(20)   NOT NULL,
    `event_type`       varchar(32)  NOT NULL,
    `from_status`      varchar(16)  DEFAULT NULL,
    `to_status`        varchar(16)  NOT NULL,
    `actor_type`       varchar(16)  NOT NULL,
    `actor_id`         bigint(20)   DEFAULT NULL,
    `reason`           varchar(500) DEFAULT NULL,
    `create_time`      datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_kyc_audit_profile_time` (`kyc_profile_id`, `create_time`),
    KEY `idx_kyc_audit_author_time` (`author_id`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Lịch sử duyệt KYC bất biến';

CREATE TABLE IF NOT EXISTS `author_withdrawal_request`
(
    `id`                           bigint(20)    NOT NULL AUTO_INCREMENT,
    `withdrawal_no`                varchar(64)   NOT NULL,
    `idempotency_key`              varchar(128)  NOT NULL,
    `author_id`                    bigint(20)    NOT NULL,
    `user_id`                      bigint(20)    NOT NULL,
    `kyc_profile_id`               bigint(20)    NOT NULL,
    `requested_xu`                 bigint(20)    NOT NULL,
    `vnd_per_xu`                   bigint(20)    NOT NULL,
    `gross_amount_vnd`             bigint(20)    NOT NULL,
    `withheld_tax_vnd`             bigint(20)    NOT NULL DEFAULT 0,
    `net_amount_vnd`               bigint(20)    NOT NULL,
    `bank_code`                    varchar(20)   NOT NULL,
    `bank_account_ciphertext`      varchar(1024) NOT NULL,
    `bank_account_last4`           char(4)       NOT NULL,
    `bank_account_name_ciphertext` varchar(1024) NOT NULL,
    `hold_idempotency_key`         varchar(128)  NOT NULL,
    `status`                       varchar(20)   NOT NULL DEFAULT 'PENDING_REVIEW',
    `release_target_status`        varchar(20)   DEFAULT NULL COMMENT 'REJECTED, FAILED hoặc CANCELLED sau khi hoàn hold',
    `payout_provider`              varchar(32)   NOT NULL DEFAULT 'MANUAL_BANK',
    `provider_reference`           varchar(128)  DEFAULT NULL,
    `requested_at`                 datetime(3)   NOT NULL,
    `reviewed_at`                  datetime(3)   DEFAULT NULL,
    `reviewed_by`                  bigint(20)    DEFAULT NULL,
    `paid_at`                      datetime(3)   DEFAULT NULL,
    `rejection_reason`             varchar(500)  DEFAULT NULL,
    `version`                      bigint(20)    NOT NULL DEFAULT 0,
    `create_time`                  datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`                  datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_author_withdrawal_no` (`withdrawal_no`),
    UNIQUE KEY `uk_author_withdrawal_idempotency` (`idempotency_key`),
    UNIQUE KEY `uk_author_withdrawal_hold_key` (`hold_idempotency_key`),
    KEY `idx_author_withdrawal_author_time` (`author_id`, `requested_at`),
    KEY `idx_author_withdrawal_status_time` (`status`, `requested_at`),
    CONSTRAINT `chk_author_withdrawal_amount` CHECK (`requested_xu` > 0 AND `vnd_per_xu` > 0),
    CONSTRAINT `chk_author_withdrawal_vnd` CHECK (`gross_amount_vnd` >= `net_amount_vnd` AND `net_amount_vnd` >= 0),
    CONSTRAINT `chk_author_withdrawal_status` CHECK (`status` IN
        ('PENDING_REVIEW', 'APPROVED', 'PROCESSING', 'SETTLEMENT_PENDING', 'RELEASE_PENDING',
         'PAID', 'REJECTED', 'FAILED', 'CANCELLED')),
    CONSTRAINT `chk_author_withdrawal_release_target` CHECK (`release_target_status` IS NULL OR
        `release_target_status` IN ('REJECTED', 'FAILED', 'CANCELLED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Yêu cầu rút thu nhập tác giả';

-- CREATE TABLE IF NOT EXISTS không nâng cấp bảng đã được tạo bởi bản migration cũ.
-- Thủ tục này chỉ bổ sung phần state machine release/settlement còn thiếu và có thể chạy lặp lại.
DROP PROCEDURE IF EXISTS `migrate_author_withdrawal_lifecycle`;
DELIMITER $$
CREATE PROCEDURE `migrate_author_withdrawal_lifecycle`()
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'author_withdrawal_request'
          AND COLUMN_NAME = 'release_target_status'
    ) THEN
        ALTER TABLE `author_withdrawal_request`
            ADD COLUMN `release_target_status` varchar(20) DEFAULT NULL
                COMMENT 'REJECTED, FAILED hoặc CANCELLED sau khi hoàn hold'
                AFTER `status`;
    END IF;

    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = 'author_withdrawal_request'
          AND CONSTRAINT_NAME = 'chk_author_withdrawal_status'
          AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        ALTER TABLE `author_withdrawal_request`
            DROP CHECK `chk_author_withdrawal_status`;
    END IF;

    ALTER TABLE `author_withdrawal_request`
        ADD CONSTRAINT `chk_author_withdrawal_status` CHECK (`status` IN
            ('PENDING_REVIEW', 'APPROVED', 'PROCESSING', 'SETTLEMENT_PENDING', 'RELEASE_PENDING',
             'PAID', 'REJECTED', 'FAILED', 'CANCELLED'));

    IF EXISTS (
        SELECT 1
        FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = 'author_withdrawal_request'
          AND CONSTRAINT_NAME = 'chk_author_withdrawal_release_target'
          AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        ALTER TABLE `author_withdrawal_request`
            DROP CHECK `chk_author_withdrawal_release_target`;
    END IF;

    ALTER TABLE `author_withdrawal_request`
        ADD CONSTRAINT `chk_author_withdrawal_release_target` CHECK (`release_target_status` IS NULL OR
            `release_target_status` IN ('REJECTED', 'FAILED', 'CANCELLED'));
END$$
DELIMITER ;

CALL `migrate_author_withdrawal_lifecycle`();
DROP PROCEDURE `migrate_author_withdrawal_lifecycle`;

-- Menu và quyền tài chính tác giả. Không dùng menu_id cố định để tránh đè dữ liệu quản trị tùy chỉnh.
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT seed.parent_id, 'Tài chính tác giả', 'novel/authorFinance', 'novel:authorFinance:view',
       1, 'fa fa-bank', 7, NOW()
FROM (
    SELECT COALESCE((
        SELECT parent_id FROM `sys_menu` WHERE perms = 'novel:author:author' LIMIT 1
    ), 0) AS parent_id
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE perms = 'novel:authorFinance:view'
);

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Xem dữ liệu KYC', NULL, 'novel:authorFinance:pii', 2, NULL, 1, NOW()
FROM (
    SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:authorFinance:view' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE perms = 'novel:authorFinance:pii'
);

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Duyệt KYC', NULL, 'novel:authorFinance:kyc', 2, NULL, 2, NOW()
FROM (
    SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:authorFinance:view' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE perms = 'novel:authorFinance:kyc'
);

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Xử lý rút tiền', NULL, 'novel:authorFinance:payout', 2, NULL, 3, NOW()
FROM (
    SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:authorFinance:view' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE perms = 'novel:authorFinance:payout'
);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT role.role_id, menu.menu_id
FROM `sys_role` role
JOIN `sys_menu` menu
  ON menu.perms IN ('novel:authorFinance:view', 'novel:authorFinance:pii',
                    'novel:authorFinance:kyc', 'novel:authorFinance:payout')
WHERE role.role_sign = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` relation
      WHERE relation.role_id = role.role_id AND relation.menu_id = menu.menu_id
  );

CREATE TABLE IF NOT EXISTS `author_withdrawal_audit`
(
    `id`                    bigint(20)   NOT NULL AUTO_INCREMENT,
    `withdrawal_request_id` bigint(20)   NOT NULL,
    `withdrawal_no`         varchar(64)  NOT NULL,
    `event_type`            varchar(32)  NOT NULL,
    `from_status`           varchar(20)  DEFAULT NULL,
    `to_status`             varchar(20)  NOT NULL,
    `actor_type`            varchar(16)  NOT NULL,
    `actor_id`              bigint(20)   DEFAULT NULL,
    `reason`                varchar(500) DEFAULT NULL,
    `create_time`           datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_withdrawal_audit_request_time` (`withdrawal_request_id`, `create_time`),
    KEY `idx_withdrawal_audit_no_time` (`withdrawal_no`, `create_time`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Lịch sử trạng thái yêu cầu rút bất biến';

DROP TRIGGER IF EXISTS `trg_author_kyc_audit_no_update`;
DROP TRIGGER IF EXISTS `trg_author_kyc_audit_no_delete`;
DROP TRIGGER IF EXISTS `trg_author_withdrawal_audit_no_update`;
DROP TRIGGER IF EXISTS `trg_author_withdrawal_audit_no_delete`;

DELIMITER $$
CREATE TRIGGER `trg_author_kyc_audit_no_update`
    BEFORE UPDATE ON `author_kyc_audit`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'author_kyc_audit is immutable';
END$$

CREATE TRIGGER `trg_author_kyc_audit_no_delete`
    BEFORE DELETE ON `author_kyc_audit`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'author_kyc_audit is immutable';
END$$

CREATE TRIGGER `trg_author_withdrawal_audit_no_update`
    BEFORE UPDATE ON `author_withdrawal_audit`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'author_withdrawal_audit is immutable';
END$$

CREATE TRIGGER `trg_author_withdrawal_audit_no_delete`
    BEFORE DELETE ON `author_withdrawal_audit`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'author_withdrawal_audit is immutable';
END$$
DELIMITER ;
