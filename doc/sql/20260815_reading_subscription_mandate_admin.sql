-- Audit bất biến cho thao tác vận hành hàng đợi thu hồi mandate VNPAY Recurring.
SET NAMES utf8mb4;

CREATE TABLE `reading_subscription_mandate_admin_audit`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `mandate_id`    BIGINT       NOT NULL,
    `user_id`       BIGINT       NOT NULL,
    `operator_id`   BIGINT       NOT NULL,
    `action`        VARCHAR(32)  NOT NULL,
    `before_status` VARCHAR(24)  NOT NULL,
    `after_status`  VARCHAR(24)  NOT NULL,
    `reason`        VARCHAR(500) NOT NULL,
    `create_time`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_rs_mandate_admin_audit_mandate` (`mandate_id`, `id`),
    KEY `idx_rs_mandate_admin_audit_operator` (`operator_id`, `create_time`, `id`),
    CONSTRAINT `chk_rs_mandate_admin_audit_action` CHECK
        (`action` IN ('RETRY_SCHEDULED')),
    CONSTRAINT `chk_rs_mandate_admin_audit_reason` CHECK
        (CHAR_LENGTH(`reason`) BETWEEN 8 AND 500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Audit bất biến cho quyết định vận hành mandate VNPAY Recurring';

DROP TRIGGER IF EXISTS `trg_rs_mandate_admin_audit_no_update`;
DROP TRIGGER IF EXISTS `trg_rs_mandate_admin_audit_no_delete`;
DELIMITER $$
CREATE TRIGGER `trg_rs_mandate_admin_audit_no_update`
BEFORE UPDATE ON `reading_subscription_mandate_admin_audit`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reading_subscription_mandate_admin_audit is immutable';
END$$
CREATE TRIGGER `trg_rs_mandate_admin_audit_no_delete`
BEFORE DELETE ON `reading_subscription_mandate_admin_audit`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reading_subscription_mandate_admin_audit is immutable';
END$$
DELIMITER ;
