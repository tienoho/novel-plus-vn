-- Nền giá cho checkout thuê bao. Không seed hoặc suy đoán giá thương mại.
SET NAMES utf8mb4;

SET @add_subscription_price = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'reading_subscription_plan'
       AND column_name = 'price_vnd') = 0,
    'ALTER TABLE `reading_subscription_plan` ADD COLUMN `price_vnd` BIGINT NULL COMMENT ''Giá bán mỗi kỳ bằng VND'' AFTER `plan_name`',
    'SELECT 1'
);
PREPARE add_subscription_price_statement FROM @add_subscription_price;
EXECUTE add_subscription_price_statement;
DEALLOCATE PREPARE add_subscription_price_statement;

SET @add_subscription_price_check = IF(
    (SELECT COUNT(*) FROM information_schema.table_constraints
     WHERE constraint_schema = DATABASE()
       AND table_name = 'reading_subscription_plan'
       AND constraint_name = 'chk_rs_plan_price') = 0,
    'ALTER TABLE `reading_subscription_plan` ADD CONSTRAINT `chk_rs_plan_price` CHECK (`price_vnd` IS NULL OR `price_vnd` BETWEEN 1000 AND 100000000)',
    'SELECT 1'
);
PREPARE add_subscription_price_check_statement FROM @add_subscription_price_check;
EXECUTE add_subscription_price_check_statement;
DEALLOCATE PREPARE add_subscription_price_check_statement;

CREATE TABLE IF NOT EXISTS `reading_subscription_purchase`
(
    `id`                            BIGINT       NOT NULL AUTO_INCREMENT,
    `out_trade_no`                  BIGINT       NOT NULL,
    `user_id`                       BIGINT       NOT NULL,
    `plan_id`                       BIGINT       NOT NULL,
    `plan_code_snapshot`            VARCHAR(32)  NOT NULL,
    `plan_name_snapshot`            VARCHAR(100) NOT NULL,
    `price_vnd_snapshot`            BIGINT       NOT NULL,
    `tickets_per_period_snapshot`   BIGINT       NOT NULL,
    `period_months_snapshot`        INT          NOT NULL,
    `ticket_validity_days_snapshot` INT          NOT NULL,
    `pay_channel`                   TINYINT      NOT NULL,
    `client_request_id`             VARCHAR(64)  NOT NULL,
    `request_hash`                  CHAR(64)     NOT NULL,
    `policy_version`                VARCHAR(32)  NOT NULL,
    `zone_id`                       VARCHAR(64)  NOT NULL,
    `status`                        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `subscription_id`               BIGINT       NULL,
    `settled_at`                    DATETIME(3)  NULL,
    `version`                       BIGINT       NOT NULL DEFAULT 0,
    `create_time`                   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`                   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `open_user_id`                  BIGINT GENERATED ALWAYS AS
        (CASE WHEN `status` IN ('PENDING', 'PAID_REVIEW') THEN `user_id` ELSE NULL END) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rsp_out_trade_no` (`out_trade_no`),
    UNIQUE KEY `uk_rsp_user_request` (`user_id`, `client_request_id`),
    UNIQUE KEY `uk_rsp_one_open_user` (`open_user_id`),
    KEY `idx_rsp_user_created` (`user_id`, `create_time`, `id`),
    KEY `idx_rsp_status_updated` (`status`, `update_time`, `id`),
    CONSTRAINT `chk_rsp_price` CHECK (`price_vnd_snapshot` BETWEEN 1000 AND 100000000),
    CONSTRAINT `chk_rsp_benefit` CHECK (`tickets_per_period_snapshot` BETWEEN 1 AND 100000),
    CONSTRAINT `chk_rsp_period` CHECK (`period_months_snapshot` BETWEEN 1 AND 12),
    CONSTRAINT `chk_rsp_validity` CHECK (`ticket_validity_days_snapshot` BETWEEN 1 AND 366),
    CONSTRAINT `chk_rsp_channel` CHECK (`pay_channel` IN (4, 5)),
    CONSTRAINT `chk_rsp_status` CHECK (`status` IN ('PENDING', 'ACTIVATED', 'PAID_REVIEW', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Đơn mua thuê bao với snapshot giá và quyền lợi';

DROP TRIGGER IF EXISTS `trg_rsp_snapshot_no_update`;
DELIMITER $$
CREATE TRIGGER `trg_rsp_snapshot_no_update`
BEFORE UPDATE ON `reading_subscription_purchase`
FOR EACH ROW
BEGIN
    IF NOT (NEW.out_trade_no <=> OLD.out_trade_no)
       OR NOT (NEW.user_id <=> OLD.user_id)
       OR NOT (NEW.plan_id <=> OLD.plan_id)
       OR NOT (NEW.plan_code_snapshot <=> OLD.plan_code_snapshot)
       OR NOT (NEW.plan_name_snapshot <=> OLD.plan_name_snapshot)
       OR NOT (NEW.price_vnd_snapshot <=> OLD.price_vnd_snapshot)
       OR NOT (NEW.tickets_per_period_snapshot <=> OLD.tickets_per_period_snapshot)
       OR NOT (NEW.period_months_snapshot <=> OLD.period_months_snapshot)
       OR NOT (NEW.ticket_validity_days_snapshot <=> OLD.ticket_validity_days_snapshot)
       OR NOT (NEW.pay_channel <=> OLD.pay_channel)
       OR NOT (NEW.client_request_id <=> OLD.client_request_id)
       OR NOT (NEW.request_hash <=> OLD.request_hash)
       OR NOT (NEW.policy_version <=> OLD.policy_version)
       OR NOT (NEW.zone_id <=> OLD.zone_id) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Không được sửa snapshot đơn mua thuê bao';
    END IF;
END$$
DELIMITER ;
