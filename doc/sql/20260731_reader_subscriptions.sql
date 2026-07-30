-- Gói thuê bao cấp Vé đọc theo kỳ. Migration chỉ dựng nền dữ liệu, không seed giá và không
-- tự kích hoạt thuê bao vì cổng thanh toán thuê bao chưa được triển khai.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `reading_subscription_plan`
(
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT,
    `plan_code`            VARCHAR(32)  NOT NULL,
    `plan_name`            VARCHAR(100) NOT NULL,
    `tickets_per_period`   BIGINT       NOT NULL,
    `period_months`        INT          NOT NULL DEFAULT 1,
    `ticket_validity_days` INT          NOT NULL,
    `status`               VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    `version`              BIGINT       NOT NULL DEFAULT 0,
    `create_time`          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`          DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rs_plan_code` (`plan_code`),
    KEY `idx_rs_plan_status` (`status`, `id`),
    CONSTRAINT `chk_rs_plan_tickets` CHECK (`tickets_per_period` BETWEEN 1 AND 100000),
    CONSTRAINT `chk_rs_plan_period` CHECK (`period_months` BETWEEN 1 AND 12),
    CONSTRAINT `chk_rs_plan_validity` CHECK (`ticket_validity_days` BETWEEN 1 AND 366),
    CONSTRAINT `chk_rs_plan_status` CHECK (`status` IN ('DRAFT', 'ACTIVE', 'RETIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Catalog gói thuê bao Vé đọc; quyền lợi được snapshot khi kích hoạt';

CREATE TABLE IF NOT EXISTS `user_reading_subscription`
(
    `id`                            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`                       BIGINT       NOT NULL,
    `plan_id`                       BIGINT       NOT NULL,
    `plan_code_snapshot`            VARCHAR(32)  NOT NULL,
    `tickets_per_period_snapshot`   BIGINT       NOT NULL,
    `period_months_snapshot`        INT          NOT NULL,
    `ticket_validity_days_snapshot` INT          NOT NULL,
    `start_at`                      DATETIME(3)  NOT NULL,
    `next_grant_at`                 DATETIME(3)  NOT NULL,
    `end_at`                        DATETIME(3)           DEFAULT NULL,
    `status`                        VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    `source_type`                   VARCHAR(32)  NOT NULL,
    `source_ref`                    VARCHAR(128) NOT NULL,
    `policy_version`                VARCHAR(32)  NOT NULL,
    `open_slot`                     TINYINT GENERATED ALWAYS AS
        (CASE WHEN `status` IN ('ACTIVE', 'PAUSED') THEN 1 ELSE NULL END) STORED,
    `version`                       BIGINT       NOT NULL DEFAULT 0,
    `create_time`                   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`                   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rs_subscription_source` (`source_type`, `source_ref`),
    UNIQUE KEY `uk_rs_subscription_open_user` (`user_id`, `open_slot`),
    KEY `idx_rs_subscription_due` (`status`, `next_grant_at`, `id`),
    KEY `idx_rs_subscription_plan` (`plan_id`, `status`, `id`),
    CONSTRAINT `chk_rs_subscription_tickets` CHECK (`tickets_per_period_snapshot` BETWEEN 1 AND 100000),
    CONSTRAINT `chk_rs_subscription_period` CHECK (`period_months_snapshot` BETWEEN 1 AND 12),
    CONSTRAINT `chk_rs_subscription_validity` CHECK (`ticket_validity_days_snapshot` BETWEEN 1 AND 366),
    CONSTRAINT `chk_rs_subscription_window` CHECK (`end_at` IS NULL OR `end_at` > `start_at`),
    CONSTRAINT `chk_rs_subscription_status` CHECK
        (`status` IN ('ACTIVE', 'PAUSED', 'CANCELLED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Thuê bao Vé đọc của người dùng với snapshot quyền lợi tại lúc kích hoạt';

CREATE TABLE IF NOT EXISTS `reading_subscription_period_grant`
(
    `id`                       BIGINT       NOT NULL AUTO_INCREMENT,
    `subscription_id`          BIGINT       NOT NULL,
    `user_id`                  BIGINT       NOT NULL,
    `period_start`             DATETIME(3)  NOT NULL,
    `period_end`               DATETIME(3)  NOT NULL,
    `ticket_amount`            BIGINT       NOT NULL,
    `ticket_expire_at`         DATETIME(3)  NOT NULL,
    `reading_ticket_ledger_id` BIGINT       NOT NULL,
    `idempotency_key`          VARCHAR(128) NOT NULL,
    `policy_version`           VARCHAR(32)  NOT NULL,
    `create_time`              DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rs_period_subscription_start` (`subscription_id`, `period_start`),
    UNIQUE KEY `uk_rs_period_idempotency` (`idempotency_key`),
    UNIQUE KEY `uk_rs_period_ticket_ledger` (`reading_ticket_ledger_id`),
    KEY `idx_rs_period_user` (`user_id`, `period_start`, `id`),
    CONSTRAINT `chk_rs_period_window` CHECK (`period_end` > `period_start`),
    CONSTRAINT `chk_rs_period_expiry` CHECK (`ticket_expire_at` > `period_start`),
    CONSTRAINT `chk_rs_period_amount` CHECK (`ticket_amount` > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Biên nhận cấp Vé đọc theo kỳ, bất biến và liên kết một-một với ledger';

DROP TRIGGER IF EXISTS `trg_rs_period_grant_no_update`;
DROP TRIGGER IF EXISTS `trg_rs_period_grant_no_delete`;

DELIMITER $$
CREATE TRIGGER `trg_rs_period_grant_no_update`
    BEFORE UPDATE ON `reading_subscription_period_grant`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reading_subscription_period_grant is immutable';
END$$

CREATE TRIGGER `trg_rs_period_grant_no_delete`
    BEFORE DELETE ON `reading_subscription_period_grant`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reading_subscription_period_grant is immutable';
END$$
DELIMITER ;
