-- Đánh giá chống lạm dụng có policy version hóa; không lưu IP hoặc device ID dạng thô.

ALTER TABLE `monthly_ticket_vote`
    ADD COLUMN `source_device_hash` CHAR(64) NULL
        COMMENT 'SHA-256 kèm muối của device cookie ẩn danh' AFTER `source_ip_hash`,
    ADD KEY `idx_mt_vote_device_time` (`source_device_hash`, `create_time`),
    ADD KEY `idx_mt_vote_ip_time` (`source_ip_hash`, `create_time`);

CREATE TABLE `gamification_abuse_policy`
(
    `policy_version`         VARCHAR(32) NOT NULL,
    `review_score_threshold` INT         NOT NULL,
    `create_time`            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`policy_version`),
    CONSTRAINT `chk_gamification_abuse_review_score` CHECK (`review_score_threshold` > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Ngưỡng review chống lạm dụng theo phiên bản policy';

CREATE TABLE `gamification_abuse_rule`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `policy_version`  VARCHAR(32)  NOT NULL,
    `rule_code`       VARCHAR(48)  NOT NULL,
    `metric_name`     VARCHAR(32)  NOT NULL,
    `threshold_value` BIGINT       NOT NULL,
    `window_minutes`  INT                   DEFAULT NULL,
    `score`           INT          NOT NULL,
    `hard_block`      TINYINT(1)   NOT NULL DEFAULT 0,
    `create_time`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gamification_abuse_rule` (`policy_version`, `rule_code`),
    CONSTRAINT `chk_gamification_abuse_metric` CHECK (`metric_name` IN
        ('ACCOUNT_AGE_HOURS', 'USER_VOTES', 'DEVICE_VOTES', 'DEVICE_USERS', 'IP_VOTES', 'IP_USERS')),
    CONSTRAINT `chk_gamification_abuse_threshold` CHECK (`threshold_value` >= 0),
    CONSTRAINT `chk_gamification_abuse_window` CHECK
        ((`metric_name` = 'ACCOUNT_AGE_HOURS' AND `window_minutes` IS NULL)
         OR (`metric_name` <> 'ACCOUNT_AGE_HOURS' AND `window_minutes` > 0)),
    CONSTRAINT `chk_gamification_abuse_score` CHECK (`score` > 0),
    CONSTRAINT `chk_gamification_abuse_block` CHECK (`hard_block` IN (0, 1))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Rule score/hard-block chống lạm dụng; không có rule mặc định';

CREATE TABLE `gamification_risk_assessment`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`           BIGINT       NOT NULL,
    `season_id`         BIGINT       NOT NULL,
    `book_id`           BIGINT       NOT NULL,
    `client_request_id` VARCHAR(64)  NOT NULL,
    `device_hash`       CHAR(64)     NOT NULL,
    `ip_hash`           CHAR(64)     NOT NULL,
    `risk_score`        INT          NOT NULL,
    `action`            VARCHAR(16)  NOT NULL,
    `matched_rules`     VARCHAR(512) NOT NULL DEFAULT '',
    `policy_version`    VARCHAR(32)  NOT NULL,
    `assessed_at`       DATETIME(3)  NOT NULL,
    `create_time`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gamification_risk_request` (`user_id`, `client_request_id`),
    KEY `idx_gamification_risk_action` (`action`, `create_time`),
    KEY `idx_gamification_risk_device` (`device_hash`, `create_time`),
    KEY `idx_gamification_risk_ip` (`ip_hash`, `create_time`),
    CONSTRAINT `chk_gamification_risk_score` CHECK (`risk_score` >= 0),
    CONSTRAINT `chk_gamification_risk_action` CHECK (`action` IN ('ALLOW', 'REVIEW', 'BLOCK'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Quyết định risk bất biến cho từng yêu cầu thắp Đuốc';

CREATE TABLE `gamification_risk_review`
(
    `assessment_id` BIGINT       NOT NULL,
    `status`        VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `reviewed_by`   BIGINT                DEFAULT NULL,
    `review_reason` VARCHAR(500)          DEFAULT NULL,
    `reviewed_at`   DATETIME(3)           DEFAULT NULL,
    `version`       BIGINT       NOT NULL DEFAULT 0,
    `create_time`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`assessment_id`),
    KEY `idx_gamification_risk_review_queue` (`status`, `create_time`),
    CONSTRAINT `chk_gamification_risk_review_status` CHECK (`status` IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT `chk_gamification_risk_review_actor` CHECK
        ((`status` = 'PENDING' AND `reviewed_by` IS NULL AND `review_reason` IS NULL AND `reviewed_at` IS NULL)
         OR (`status` <> 'PENDING' AND `reviewed_by` IS NOT NULL
             AND `review_reason` IS NOT NULL AND `reviewed_at` IS NOT NULL))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Trạng thái xử lý queue risk, tách khỏi assessment bất biến';

CREATE TABLE `gamification_risk_review_audit`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `assessment_id` BIGINT       NOT NULL,
    `from_status`   VARCHAR(16)  NOT NULL,
    `to_status`     VARCHAR(16)  NOT NULL,
    `operator_id`   BIGINT       NOT NULL,
    `reason`        VARCHAR(500) NOT NULL,
    `create_time`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_gamification_risk_audit` (`assessment_id`, `create_time`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Audit bất biến cho thao tác review risk';

DROP TRIGGER IF EXISTS `trg_monthly_ticket_vote_immutable_columns`;
DELIMITER //
CREATE TRIGGER `trg_monthly_ticket_vote_immutable_columns`
    BEFORE UPDATE ON `monthly_ticket_vote` FOR EACH ROW
BEGIN
    IF NOT (NEW.`season_id` <=> OLD.`season_id`)
        OR NOT (NEW.`book_id` <=> OLD.`book_id`)
        OR NOT (NEW.`author_id` <=> OLD.`author_id`)
        OR NOT (NEW.`user_id` <=> OLD.`user_id`)
        OR NOT (NEW.`ticket_count` <=> OLD.`ticket_count`)
        OR NOT (NEW.`ledger_id` <=> OLD.`ledger_id`)
        OR NOT (NEW.`idempotency_key` <=> OLD.`idempotency_key`)
        OR NOT (NEW.`request_hash` <=> OLD.`request_hash`)
        OR NOT (NEW.`client_request_id` <=> OLD.`client_request_id`)
        OR NOT (NEW.`source_ip_hash` <=> OLD.`source_ip_hash`)
        OR NOT (NEW.`source_device_hash` <=> OLD.`source_device_hash`)
        OR NOT (NEW.`policy_version` <=> OLD.`policy_version`) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'monthly_ticket_vote content is immutable';
    END IF;
    IF OLD.`status` = 'VOIDED'
        AND (NOT (NEW.`status` <=> OLD.`status`)
            OR NOT (NEW.`voided_ledger_id` <=> OLD.`voided_ledger_id`)
            OR NOT (NEW.`void_reason` <=> OLD.`void_reason`)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'voided monthly_ticket_vote cannot be restored or changed';
    END IF;
    IF OLD.`status` = 'VALID' AND NEW.`status` = 'VALID'
        AND (NOT (NEW.`voided_ledger_id` <=> OLD.`voided_ledger_id`)
            OR NOT (NEW.`void_reason` <=> OLD.`void_reason`)) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'monthly_ticket_vote void details require VOIDED status';
    END IF;
END//

CREATE TRIGGER `trg_gamification_abuse_policy_no_update`
    BEFORE UPDATE ON `gamification_abuse_policy` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification_abuse_policy is immutable';
END//
CREATE TRIGGER `trg_gamification_abuse_policy_no_delete`
    BEFORE DELETE ON `gamification_abuse_policy` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification_abuse_policy is immutable';
END//
CREATE TRIGGER `trg_gamification_abuse_rule_no_update`
    BEFORE UPDATE ON `gamification_abuse_rule` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification_abuse_rule is immutable';
END//
CREATE TRIGGER `trg_gamification_abuse_rule_no_delete`
    BEFORE DELETE ON `gamification_abuse_rule` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification_abuse_rule is immutable';
END//
CREATE TRIGGER `trg_gamification_risk_assessment_no_update`
    BEFORE UPDATE ON `gamification_risk_assessment` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification_risk_assessment is immutable';
END//
CREATE TRIGGER `trg_gamification_risk_assessment_no_delete`
    BEFORE DELETE ON `gamification_risk_assessment` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification_risk_assessment is immutable';
END//
CREATE TRIGGER `trg_gamification_risk_review_audit_no_update`
    BEFORE UPDATE ON `gamification_risk_review_audit` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification_risk_review_audit is immutable';
END//
CREATE TRIGGER `trg_gamification_risk_review_audit_no_delete`
    BEFORE DELETE ON `gamification_risk_review_audit` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification_risk_review_audit is immutable';
END//
DELIMITER ;
