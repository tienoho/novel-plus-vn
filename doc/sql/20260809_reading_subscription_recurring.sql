-- Nền thuê bao tự gia hạn. Provider VNPAY Recurring mặc định tắt cho tới khi có credential được duyệt.
SET NAMES utf8mb4;

ALTER TABLE `reading_subscription_plan`
    ADD COLUMN `plan_version` BIGINT NOT NULL DEFAULT 1 AFTER `plan_name`,
    ADD COLUMN `price_xu` BIGINT NULL AFTER `price_vnd`,
    ADD COLUMN `price_announced_at` DATETIME(3) NULL AFTER `price_xu`,
    ADD COLUMN `price_effective_at` DATETIME(3) NULL AFTER `price_announced_at`,
    ADD CONSTRAINT `chk_rs_plan_business_version` CHECK (`plan_version` >= 1),
    ADD CONSTRAINT `chk_rs_plan_price_xu` CHECK (`price_xu` IS NULL OR `price_xu` BETWEEN 1 AND 100000000),
    ADD CONSTRAINT `chk_rs_plan_price_notice` CHECK
        (`price_effective_at` IS NULL OR `price_effective_at` >= DATE_ADD(`price_announced_at`, INTERVAL 7 DAY));

ALTER TABLE `user_reading_subscription`
    DROP INDEX `uk_rs_subscription_open_user`,
    DROP COLUMN `open_slot`;

ALTER TABLE `user_reading_subscription`
    DROP CHECK `chk_rs_subscription_status`;

ALTER TABLE `user_reading_subscription`
    MODIFY COLUMN `status` VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    ADD COLUMN `current_period_start` DATETIME(3) NULL AFTER `start_at`,
    ADD COLUMN `current_period_end` DATETIME(3) NULL AFTER `current_period_start`,
    ADD COLUMN `next_renewal_at` DATETIME(3) NULL AFTER `current_period_end`,
    ADD COLUMN `auto_renew` TINYINT(1) NOT NULL DEFAULT 0 AFTER `end_at`,
    ADD COLUMN `primary_funding_source` VARCHAR(24) NULL AFTER `auto_renew`,
    ADD COLUMN `fallback_funding_source` VARCHAR(24) NULL AFTER `primary_funding_source`,
    ADD COLUMN `plan_version_snapshot` BIGINT NOT NULL DEFAULT 1 AFTER `plan_code_snapshot`,
    ADD COLUMN `price_vnd_snapshot` BIGINT NULL AFTER `plan_version_snapshot`,
    ADD COLUMN `price_xu_snapshot` BIGINT NULL AFTER `price_vnd_snapshot`,
    ADD COLUMN `accepted_plan_version` BIGINT NOT NULL DEFAULT 1 AFTER `price_xu_snapshot`,
    ADD COLUMN `price_consent_at` DATETIME(3) NULL AFTER `accepted_plan_version`,
    ADD COLUMN `cancel_requested_at` DATETIME(3) NULL AFTER `price_consent_at`,
    ADD COLUMN `open_slot` TINYINT GENERATED ALWAYS AS
        (CASE WHEN `status` IN ('ACTIVE', 'PAST_DUE', 'PENDING_PRICE_CONSENT', 'CANCEL_AT_PERIOD_END')
              THEN 1 ELSE NULL END) STORED,
    ADD UNIQUE KEY `uk_rs_subscription_open_user` (`user_id`, `open_slot`),
    ADD KEY `idx_rs_subscription_renewal` (`auto_renew`, `status`, `next_renewal_at`, `id`),
    ADD CONSTRAINT `chk_rs_subscription_status` CHECK
        (`status` IN ('ACTIVE', 'PAST_DUE', 'PENDING_PRICE_CONSENT',
                      'CANCEL_AT_PERIOD_END', 'CANCELLED', 'EXPIRED')),
    ADD CONSTRAINT `chk_rs_subscription_funding_primary` CHECK
        (`primary_funding_source` IS NULL OR `primary_funding_source` IN ('VNPAY_RECURRING', 'WALLET_XU')),
    ADD CONSTRAINT `chk_rs_subscription_funding_fallback` CHECK
        (`fallback_funding_source` IS NULL OR `fallback_funding_source` IN ('VNPAY_RECURRING', 'WALLET_XU')),
    ADD CONSTRAINT `chk_rs_subscription_funding_distinct` CHECK
        (`fallback_funding_source` IS NULL OR `fallback_funding_source` <> `primary_funding_source`),
    ADD CONSTRAINT `chk_rs_subscription_plan_versions` CHECK
        (`plan_version_snapshot` >= 1 AND `accepted_plan_version` >= 1),
    ADD CONSTRAINT `chk_rs_subscription_period_window` CHECK
        (`current_period_start` IS NULL OR `current_period_end` > `current_period_start`);

ALTER TABLE `reading_subscription_purchase`
    ADD COLUMN `plan_version_snapshot` BIGINT NOT NULL DEFAULT 1 AFTER `plan_name_snapshot`,
    ADD COLUMN `price_xu_snapshot` BIGINT NULL AFTER `price_vnd_snapshot`,
    ADD COLUMN `auto_renew` TINYINT(1) NOT NULL DEFAULT 0 AFTER `price_xu_snapshot`,
    ADD COLUMN `primary_funding_source` VARCHAR(24) NULL AFTER `auto_renew`,
    ADD COLUMN `fallback_funding_source` VARCHAR(24) NULL AFTER `primary_funding_source`,
    ADD COLUMN `accepted_plan_version` BIGINT NOT NULL DEFAULT 1 AFTER `fallback_funding_source`,
    ADD CONSTRAINT `chk_rsp_plan_version` CHECK
        (`plan_version_snapshot` >= 1 AND `accepted_plan_version` >= 1),
    ADD CONSTRAINT `chk_rsp_price_xu` CHECK (`price_xu_snapshot` IS NULL OR `price_xu_snapshot` > 0),
    ADD CONSTRAINT `chk_rsp_funding_primary` CHECK
        (`primary_funding_source` IS NULL OR `primary_funding_source` IN ('VNPAY_RECURRING', 'WALLET_XU')),
    ADD CONSTRAINT `chk_rsp_funding_fallback` CHECK
        (`fallback_funding_source` IS NULL OR `fallback_funding_source` IN ('VNPAY_RECURRING', 'WALLET_XU')),
    ADD CONSTRAINT `chk_rsp_funding_distinct` CHECK
        (`fallback_funding_source` IS NULL OR `fallback_funding_source` <> `primary_funding_source`);

DROP TRIGGER IF EXISTS `trg_rsp_recurring_snapshot_no_update`;
DELIMITER $$
CREATE TRIGGER `trg_rsp_recurring_snapshot_no_update`
BEFORE UPDATE ON `reading_subscription_purchase`
FOR EACH ROW
BEGIN
    IF NOT (NEW.plan_version_snapshot <=> OLD.plan_version_snapshot)
       OR NOT (NEW.price_xu_snapshot <=> OLD.price_xu_snapshot)
       OR NOT (NEW.auto_renew <=> OLD.auto_renew)
       OR NOT (NEW.primary_funding_source <=> OLD.primary_funding_source)
       OR NOT (NEW.fallback_funding_source <=> OLD.fallback_funding_source)
       OR NOT (NEW.accepted_plan_version <=> OLD.accepted_plan_version) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Không được sửa snapshot tự gia hạn của đơn mua thuê bao';
    END IF;
END$$
DELIMITER ;

CREATE TABLE `reading_subscription_mandate`
(
    `id`                        BIGINT        NOT NULL AUTO_INCREMENT,
    `user_id`                   BIGINT        NOT NULL,
    `provider`                  VARCHAR(24)   NOT NULL,
    `merchant_reference`        VARCHAR(100)  NOT NULL,
    `provider_recurring_id`     VARCHAR(64)   NULL,
    `provider_token_ciphertext` VARCHAR(2048) NULL,
    `token_expire_at`           DATETIME(3)   NULL,
    `status`                    VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
    `consented_at`              DATETIME(3)   NULL,
    `revoked_at`                DATETIME(3)   NULL,
    `version`                   BIGINT        NOT NULL DEFAULT 0,
    `create_time`               DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`               DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `open_user_id`              BIGINT GENERATED ALWAYS AS
        (CASE WHEN `status` IN ('PENDING', 'ACTIVE') THEN `user_id` ELSE NULL END) STORED,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rs_mandate_merchant_reference` (`merchant_reference`),
    UNIQUE KEY `uk_rs_mandate_open_user` (`provider`, `open_user_id`),
    KEY `idx_rs_mandate_user` (`user_id`, `id`),
    CONSTRAINT `chk_rs_mandate_provider` CHECK (`provider` = 'VNPAY_RECURRING'),
    CONSTRAINT `chk_rs_mandate_status` CHECK (`status` IN ('PENDING', 'ACTIVE', 'REVOKED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Ủy quyền VNPAY Recurring; token provider luôn được mã hóa AES-GCM';

CREATE TABLE `reading_subscription_renewal_cycle`
(
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `subscription_id`       BIGINT       NOT NULL,
    `user_id`               BIGINT       NOT NULL,
    `period_start`          DATETIME(3)  NOT NULL,
    `period_end`            DATETIME(3)  NOT NULL,
    `grace_end_at`          DATETIME(3)  NOT NULL,
    `plan_version_snapshot` BIGINT       NOT NULL,
    `price_vnd_snapshot`    BIGINT       NULL,
    `price_xu_snapshot`     BIGINT       NULL,
    `status`                VARCHAR(24)  NOT NULL DEFAULT 'DUE',
    `attempt_count`         INT          NOT NULL DEFAULT 0,
    `next_attempt_at`       DATETIME(3)  NOT NULL,
    `settled_source`        VARCHAR(24)  NULL,
    `settled_reference`     VARCHAR(128) NULL,
    `settled_at`            DATETIME(3)  NULL,
    `idempotency_key`       VARCHAR(128) NOT NULL,
    `version`               BIGINT       NOT NULL DEFAULT 0,
    `create_time`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rs_renewal_subscription_period` (`subscription_id`, `period_start`),
    UNIQUE KEY `uk_rs_renewal_idempotency` (`idempotency_key`),
    KEY `idx_rs_renewal_due` (`status`, `next_attempt_at`, `id`),
    KEY `idx_rs_renewal_user` (`user_id`, `period_start`, `id`),
    CONSTRAINT `chk_rs_renewal_window` CHECK (`period_end` > `period_start` AND `grace_end_at` > `period_start`),
    CONSTRAINT `chk_rs_renewal_version` CHECK (`plan_version_snapshot` >= 1),
    CONSTRAINT `chk_rs_renewal_price` CHECK
        ((`price_vnd_snapshot` IS NOT NULL AND `price_vnd_snapshot` > 0)
          OR (`price_xu_snapshot` IS NOT NULL AND `price_xu_snapshot` > 0)),
    CONSTRAINT `chk_rs_renewal_status` CHECK
        (`status` IN ('DUE', 'PROCESSING', 'PROVIDER_PENDING', 'RETRY_WAIT',
                      'SETTLED', 'FAILED', 'GRACE_EXPIRED')),
    CONSTRAINT `chk_rs_renewal_source` CHECK
        (`settled_source` IS NULL OR `settled_source` IN ('VNPAY_RECURRING', 'WALLET_XU'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Một cycle gia hạn duy nhất cho mỗi kỳ thuê bao';

CREATE TABLE `reading_subscription_renewal_attempt`
(
    `id`                    BIGINT        NOT NULL AUTO_INCREMENT,
    `cycle_id`              BIGINT        NOT NULL,
    `attempt_no`            INT           NOT NULL,
    `funding_source`        VARCHAR(24)   NOT NULL,
    `provider_request_id`   VARCHAR(64)   NULL,
    `provider_transaction_id` VARCHAR(64) NULL,
    `status`                VARCHAR(24)   NOT NULL,
    `response_code`         VARCHAR(32)   NULL,
    `response_message`      VARCHAR(500)  NULL,
    `request_hash`          CHAR(64)      NOT NULL,
    `started_at`            DATETIME(3)   NOT NULL,
    `completed_at`          DATETIME(3)   NULL,
    `version`               BIGINT        NOT NULL DEFAULT 0,
    `create_time`           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`           DATETIME(3)   NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rs_renewal_attempt_no` (`cycle_id`, `attempt_no`),
    UNIQUE KEY `uk_rs_renewal_provider_request` (`provider_request_id`),
    KEY `idx_rs_renewal_attempt_status` (`status`, `update_time`, `id`),
    CONSTRAINT `chk_rs_renewal_attempt_no` CHECK (`attempt_no` BETWEEN 1 AND 10),
    CONSTRAINT `chk_rs_renewal_attempt_source` CHECK
        (`funding_source` IN ('VNPAY_RECURRING', 'WALLET_XU')),
    CONSTRAINT `chk_rs_renewal_attempt_status` CHECK
        (`status` IN ('STARTED', 'PENDING', 'SETTLED', 'FAILED', 'UNKNOWN'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Từng lần thử nguồn thanh toán của cycle gia hạn';

CREATE TABLE `reading_subscription_price_consent`
(
    `id`                    BIGINT       NOT NULL AUTO_INCREMENT,
    `subscription_id`       BIGINT       NOT NULL,
    `user_id`               BIGINT       NOT NULL,
    `plan_version`          BIGINT       NOT NULL,
    `price_vnd_snapshot`    BIGINT       NULL,
    `price_xu_snapshot`     BIGINT       NULL,
    `consented_at`          DATETIME(3)  NOT NULL,
    `client_request_id`     VARCHAR(64)  NOT NULL,
    `create_time`           DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rs_price_consent_version` (`subscription_id`, `plan_version`),
    UNIQUE KEY `uk_rs_price_consent_request` (`user_id`, `client_request_id`),
    CONSTRAINT `chk_rs_price_consent_version` CHECK (`plan_version` >= 1),
    CONSTRAINT `chk_rs_price_consent_price` CHECK
        ((`price_vnd_snapshot` IS NOT NULL AND `price_vnd_snapshot` > 0)
          OR (`price_xu_snapshot` IS NOT NULL AND `price_xu_snapshot` > 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Bằng chứng người dùng chấp nhận snapshot giá mới';

CREATE TABLE `reading_subscription_renewal_admin_audit`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `cycle_id`         BIGINT       NOT NULL,
    `user_id`          BIGINT       NOT NULL,
    `operator_id`      BIGINT       NOT NULL,
    `action`           VARCHAR(32)  NOT NULL,
    `before_status`    VARCHAR(24)  NOT NULL,
    `after_status`     VARCHAR(24)  NOT NULL,
    `reason`           VARCHAR(500) NOT NULL,
    `create_time`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_rs_renewal_audit_cycle` (`cycle_id`, `id`),
    KEY `idx_rs_renewal_audit_operator` (`operator_id`, `create_time`, `id`),
    CONSTRAINT `chk_rs_renewal_audit_action` CHECK (`action` IN ('RETRY_SCHEDULED')),
    CONSTRAINT `chk_rs_renewal_audit_reason` CHECK (CHAR_LENGTH(`reason`) BETWEEN 8 AND 500)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Audit bất biến cho thao tác vận hành cycle gia hạn';

DROP TRIGGER IF EXISTS `trg_rs_price_consent_no_update`;
DROP TRIGGER IF EXISTS `trg_rs_price_consent_no_delete`;
DROP TRIGGER IF EXISTS `trg_rs_renewal_admin_audit_no_update`;
DROP TRIGGER IF EXISTS `trg_rs_renewal_admin_audit_no_delete`;
DELIMITER $$
CREATE TRIGGER `trg_rs_price_consent_no_update`
BEFORE UPDATE ON `reading_subscription_price_consent`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reading_subscription_price_consent is immutable';
END$$
CREATE TRIGGER `trg_rs_price_consent_no_delete`
BEFORE DELETE ON `reading_subscription_price_consent`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reading_subscription_price_consent is immutable';
END$$
CREATE TRIGGER `trg_rs_renewal_admin_audit_no_update`
BEFORE UPDATE ON `reading_subscription_renewal_admin_audit`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reading_subscription_renewal_admin_audit is immutable';
END$$
CREATE TRIGGER `trg_rs_renewal_admin_audit_no_delete`
BEFORE DELETE ON `reading_subscription_renewal_admin_audit`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reading_subscription_renewal_admin_audit is immutable';
END$$
DELIMITER ;
