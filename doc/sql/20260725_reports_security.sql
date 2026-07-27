-- Migration script for Requirement R5 (Reports, Tax & Receipts) and R6 (Security & Audit)
SET NAMES utf8mb4;

-- 1. Financial Vouchers & Receipts Table
CREATE TABLE IF NOT EXISTS `financial_voucher` (
    `id`                           bigint(20)    NOT NULL AUTO_INCREMENT,
    `voucher_no`                   varchar(64)   NOT NULL,
    `voucher_type`                 varchar(32)   NOT NULL COMMENT 'RECHARGE_RECEIPT or AUTHOR_PAYOUT_VOUCHER',
    `reference_type`               varchar(32)   NOT NULL COMMENT 'ORDER_PAY or AUTHOR_WITHDRAWAL_REQUEST',
    `reference_id`                 varchar(64)   NOT NULL,
    `payer_name`                   varchar(255)  NOT NULL,
    `payer_tax_code`               varchar(64)   DEFAULT NULL,
    `payee_name`                   varchar(255)  NOT NULL,
    `payee_tax_code`               varchar(64)   DEFAULT NULL,
    `gross_amount_vnd`             bigint(20)    NOT NULL,
    `tax_amount_vnd`               bigint(20)    NOT NULL DEFAULT 0 COMMENT 'VAT for recharge, PIT for payout',
    `net_amount_vnd`               bigint(20)    NOT NULL,
    `currency`                     varchar(8)    NOT NULL DEFAULT 'VND',
    `status`                       varchar(16)    NOT NULL DEFAULT 'ISSUED' COMMENT 'ISSUED, CANCELLED',
    `issued_at`                    datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `create_time`                  datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`                  datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_voucher_no` (`voucher_no`),
    UNIQUE KEY `uk_voucher_ref` (`reference_type`, `reference_id`),
    KEY `idx_voucher_type_time` (`voucher_type`, `issued_at`),
    CONSTRAINT `chk_voucher_amounts` CHECK (`gross_amount_vnd` >= 0 AND `net_amount_vnd` >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Chứng từ nạp tiền và phiếu chi tài chính';

-- 2. Two-Factor Authentication (2FA) Table
CREATE TABLE IF NOT EXISTS `user_2fa` (
    `id`                           bigint(20)    NOT NULL AUTO_INCREMENT,
    `user_id`                      bigint(20)    NOT NULL,
    `secret_key_ciphertext`        varchar(512)  NOT NULL,
    `is_enabled`                   tinyint(1)    NOT NULL DEFAULT 0,
    `backup_codes_json`            varchar(1024) DEFAULT NULL,
    `enabled_at`                   datetime(3)   DEFAULT NULL,
    `last_verified_at`             datetime(3)   DEFAULT NULL COMMENT 'Thời điểm xác thực thành công gần nhất',
    `create_time`                  datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`                  datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_2fa_user` (`user_id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Xác thực 2 lớp (2FA TOTP)';

-- 3. System Audit Log Table
CREATE TABLE IF NOT EXISTS `sys_audit_log` (
    `id`                           bigint(20)    NOT NULL AUTO_INCREMENT,
    `module`                       varchar(64)   NOT NULL COMMENT 'AUTH, PAYMENT, PAYOUT, KYC, CONTENT, SYSTEM',
    `event_type`                   varchar(64)   NOT NULL,
    `actor_id`                     bigint(20)    DEFAULT NULL,
    `actor_username`               varchar(100)  DEFAULT NULL,
    `actor_ip`                     varchar(64)   NOT NULL,
    `user_agent`                   varchar(500)  DEFAULT NULL,
    `request_url`                  varchar(255)  DEFAULT NULL,
    `request_params`               text          DEFAULT NULL,
    `status`                       varchar(16)   NOT NULL COMMENT 'SUCCESS or FAILURE',
    `detail`                       text          DEFAULT NULL,
    `create_time`                  datetime(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_sys_audit_module_time` (`module`, `create_time`),
    KEY `idx_sys_audit_actor_time` (`actor_id`, `create_time`),
    KEY `idx_sys_audit_event_time` (`event_type`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = 'Nhật ký kiểm toán bảo mật bất biến';

-- 4. Immutable Triggers for sys_audit_log
DROP TRIGGER IF EXISTS `trg_sys_audit_log_no_update`;
DROP TRIGGER IF EXISTS `trg_sys_audit_log_no_delete`;

DELIMITER $$
CREATE TRIGGER `trg_sys_audit_log_no_update`
    BEFORE UPDATE ON `sys_audit_log`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_audit_log is immutable';
END$$

CREATE TRIGGER `trg_sys_audit_log_no_delete`
    BEFORE DELETE ON `sys_audit_log`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'sys_audit_log is immutable';
END$$
DELIMITER ;

-- 5. Admin Menu Registration for Reports & Security
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT 0, 'Báo cáo & Tài chính', 'novel/reports', 'novel:reports:view', 1, 'fa fa-line-chart', 8, NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:reports:view');

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT menu_id, 'Báo cáo doanh thu & Thuế', 'novel/reports/revenue', 'novel:reports:revenue', 2, NULL, 1, NOW()
FROM `sys_menu` WHERE perms = 'novel:reports:view' LIMIT 1;

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT menu_id, 'Quản lý chứng từ', 'novel/reports/vouchers', 'novel:reports:vouchers', 2, NULL, 2, NOW()
FROM `sys_menu` WHERE perms = 'novel:reports:view' LIMIT 1;

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT menu_id, 'Nhật ký kiểm toán bảo mật', 'system/auditLog', 'sys:auditLog:view', 2, 'fa fa-shield', 3, NOW()
FROM `sys_menu` WHERE perms = 'sys:log:log' LIMIT 1;
