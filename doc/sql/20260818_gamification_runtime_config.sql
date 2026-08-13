-- Cấu hình runtime và policy gamification được version hóa, có maker-checker và audit.
-- Migration chỉ backfill dữ liệu hiện có vào policy v1; không thay đổi kết quả nghiệp vụ lịch sử.

SET NAMES utf8mb4;

CREATE TABLE `gamification_policy_bundle`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `policy_version`   VARCHAR(32)  NOT NULL,
    `status`           VARCHAR(24)  NOT NULL DEFAULT 'DRAFT',
    `content_hash`     CHAR(64)     NOT NULL,
    `created_by`       BIGINT       NOT NULL,
    `submitted_by`     BIGINT                DEFAULT NULL,
    `approved_by`      BIGINT                DEFAULT NULL,
    `published_by`     BIGINT                DEFAULT NULL,
    `change_reason`    VARCHAR(500) NOT NULL,
    `submitted_at`     DATETIME(3)           DEFAULT NULL,
    `approved_at`      DATETIME(3)           DEFAULT NULL,
    `published_at`     DATETIME(3)           DEFAULT NULL,
    `archived_at`      DATETIME(3)           DEFAULT NULL,
    `version`          BIGINT       NOT NULL DEFAULT 0,
    `create_time`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gamification_policy_version` (`policy_version`),
    KEY `idx_gamification_policy_status` (`status`, `update_time`),
    CONSTRAINT `chk_gamification_policy_status` CHECK (`status` IN
        ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'PUBLISHED', 'ARCHIVED', 'REJECTED')),
    CONSTRAINT `chk_gamification_policy_maker_checker` CHECK
        (`approved_by` IS NULL OR `approved_by` <> `created_by`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Header cho một bộ policy gamification bất biến sau khi submit';

CREATE TABLE `gamification_policy_bundle_audit`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `policy_id`      BIGINT       NOT NULL,
    `event_type`     VARCHAR(32)  NOT NULL,
    `from_status`    VARCHAR(24)           DEFAULT NULL,
    `to_status`      VARCHAR(24)  NOT NULL,
    `operator_id`    BIGINT       NOT NULL,
    `reason`         VARCHAR(500) NOT NULL,
    `before_hash`    CHAR(64)              DEFAULT NULL,
    `after_hash`     CHAR(64)              DEFAULT NULL,
    `create_time`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_gamification_policy_audit` (`policy_id`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Audit bất biến cho lifecycle policy gamification';

ALTER TABLE `quest_definition`
    ADD COLUMN `policy_version` VARCHAR(32) NOT NULL DEFAULT 'v1' AFTER `id`,
    DROP INDEX `uk_quest_definition_code`,
    ADD UNIQUE KEY `uk_quest_definition_version_code` (`policy_version`, `quest_code`);

ALTER TABLE `quest_reward`
    ADD COLUMN `policy_version` VARCHAR(32) NOT NULL DEFAULT 'v1' AFTER `id`,
    DROP INDEX `uk_quest_reward_scope`,
    ADD UNIQUE KEY `uk_quest_reward_version_scope`
        (`policy_version`, `quest_code`, `campaign_code`, `reward_type`);

ALTER TABLE `realm_catalog`
    ADD COLUMN `policy_version` VARCHAR(32) NOT NULL DEFAULT 'v1' AFTER `id`,
    DROP INDEX `uk_realm_catalog_code`,
    ADD UNIQUE KEY `uk_realm_catalog_version_code` (`policy_version`, `realm_code`);

INSERT INTO `gamification_policy_bundle`
    (`id`, `policy_version`, `status`, `content_hash`, `created_by`, `submitted_by`,
     `approved_by`, `published_by`, `change_reason`, `submitted_at`, `approved_at`, `published_at`)
VALUES
    (1, 'v1', 'PUBLISHED', SHA2('khoi-thu-gamification-policy-v1', 256),
     1, 1, 2, 2, 'Backfill policy v1 hiện hành', NOW(3), NOW(3), NOW(3));

INSERT INTO `gamification_policy_bundle_audit`
    (`policy_id`, `event_type`, `from_status`, `to_status`, `operator_id`, `reason`, `after_hash`)
VALUES
    (1, 'BACKFILLED', NULL, 'PUBLISHED', 1, 'Backfill policy v1 hiện hành',
     SHA2('khoi-thu-gamification-policy-v1', 256));

CREATE TABLE `gamification_runtime_config`
(
    `id`                                  BIGINT       NOT NULL AUTO_INCREMENT,
    `revision_no`                         BIGINT       NOT NULL,
    `revision_code`                       VARCHAR(64)  NOT NULL,
    `source_revision_id`                  BIGINT                DEFAULT NULL,
    `source_hash`                         CHAR(64)              DEFAULT NULL,
    `status`                              VARCHAR(24)  NOT NULL DEFAULT 'DRAFT',
    `activation_class`                    VARCHAR(16)  NOT NULL DEFAULT 'IMMEDIATE',
    `high_risk`                           TINYINT(1)   NOT NULL DEFAULT 0,
    `policy_version`                      VARCHAR(32)  NOT NULL,
    `zone_id`                             VARCHAR(64)  NOT NULL,

    `event_enabled`                       TINYINT(1)   NOT NULL DEFAULT 0,
    `event_drain_batch_size`              INT          NOT NULL,
    `event_drain_delay_ms`                BIGINT       NOT NULL,
    `event_max_attempt`                   INT          NOT NULL,

    `ticket_enabled`                      TINYINT(1)   NOT NULL DEFAULT 0,
    `ticket_lot_validity_days`            INT          NOT NULL,
    `ticket_expiry_cron`                  VARCHAR(64)  NOT NULL,
    `ticket_expiry_batch_size`            INT          NOT NULL,
    `ticket_max_grant_per_batch`          INT          NOT NULL,

    `vote_enabled`                        TINYINT(1)   NOT NULL DEFAULT 0,
    `vote_allow_crawled_books`            TINYINT(1)   NOT NULL DEFAULT 0,
    `vote_ip_hash_key_id`                 VARCHAR(64)  NOT NULL,
    `vote_max_tickets_per_request`        INT          NOT NULL,
    `vote_max_votes_per_day`              INT          NOT NULL,
    `vote_max_tickets_per_day`            INT          NOT NULL,
    `vote_max_tickets_per_book_season`    INT          NOT NULL,
    `vote_max_lots_per_spend`             INT          NOT NULL,

    `quest_enabled`                       TINYINT(1)   NOT NULL DEFAULT 0,
    `quest_heartbeat_interval_seconds`    INT          NOT NULL,
    `quest_heartbeat_max_minutes_day`     INT          NOT NULL,

    `realm_enabled`                       TINYINT(1)   NOT NULL DEFAULT 0,
    `realm_change_cooldown_hours`         INT          NOT NULL,

    `season_enabled`                      TINYINT(1)   NOT NULL DEFAULT 0,
    `season_close_cron`                   VARCHAR(64)  NOT NULL,
    `season_close_drain_seconds`          INT          NOT NULL,
    `season_resume_delay_ms`              BIGINT       NOT NULL,
    `season_review_window_hours`          INT          NOT NULL,

    `reward_enabled`                      TINYINT(1)   NOT NULL DEFAULT 0,
    `reward_claim_window_days`            INT          NOT NULL,
    `reward_release_cron`                 VARCHAR(64)  NOT NULL,

    `job_lease_seconds`                   INT          NOT NULL,
    `job_batch_size`                      INT          NOT NULL,

    `config_hash`                         CHAR(64)     NOT NULL,
    `created_by`                          BIGINT       NOT NULL,
    `submitted_by`                        BIGINT                DEFAULT NULL,
    `approved_by`                         BIGINT                DEFAULT NULL,
    `scheduled_by`                        BIGINT                DEFAULT NULL,
    `activated_by`                        BIGINT                DEFAULT NULL,
    `change_reason`                       VARCHAR(500) NOT NULL,
    `submitted_at`                        DATETIME(3)           DEFAULT NULL,
    `approved_at`                         DATETIME(3)           DEFAULT NULL,
    `effective_at`                        DATETIME(3)           DEFAULT NULL,
    `activated_at`                        DATETIME(3)           DEFAULT NULL,
    `archived_at`                         DATETIME(3)           DEFAULT NULL,
    `version`                             BIGINT       NOT NULL DEFAULT 0,
    `active_slot`                         TINYINT GENERATED ALWAYS AS
        (CASE WHEN `status` = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
    `scheduled_slot`                      TINYINT GENERATED ALWAYS AS
        (CASE WHEN `status` = 'SCHEDULED' THEN 1 ELSE NULL END) STORED,
    `create_time`                         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`                         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gamification_config_revision` (`revision_no`),
    UNIQUE KEY `uk_gamification_config_code` (`revision_code`),
    UNIQUE KEY `uk_gamification_config_source_hash` (`source_hash`),
    UNIQUE KEY `uk_gamification_config_active` (`active_slot`),
    UNIQUE KEY `uk_gamification_config_scheduled` (`scheduled_slot`),
    KEY `idx_gamification_config_status` (`status`, `effective_at`, `id`),
    CONSTRAINT `chk_gamification_config_status` CHECK (`status` IN
        ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'SCHEDULED', 'ACTIVE',
         'ARCHIVED', 'REJECTED', 'CANCELLED')),
    CONSTRAINT `chk_gamification_config_activation` CHECK (`activation_class` IN
        ('IMMEDIATE', 'NEXT_DAY', 'NEXT_SEASON')),
    CONSTRAINT `chk_gamification_config_positive` CHECK
        (`event_drain_batch_size` > 0 AND `event_drain_delay_ms` > 0 AND `event_max_attempt` > 0
         AND `ticket_lot_validity_days` > 0 AND `ticket_expiry_batch_size` > 0
         AND `ticket_max_grant_per_batch` > 0 AND `vote_max_tickets_per_request` > 0
         AND `vote_max_votes_per_day` > 0 AND `vote_max_tickets_per_day` > 0
         AND `vote_max_tickets_per_book_season` > 0 AND `vote_max_lots_per_spend` > 0
         AND `quest_heartbeat_interval_seconds` > 0 AND `quest_heartbeat_max_minutes_day` > 0
         AND `realm_change_cooldown_hours` > 0 AND `season_close_drain_seconds` >= 0
         AND `season_resume_delay_ms` > 0 AND `season_review_window_hours` > 0
         AND `reward_claim_window_days` > 0 AND `job_lease_seconds` > 0 AND `job_batch_size` > 0),
    CONSTRAINT `chk_gamification_config_resource_bounds` CHECK
        (`event_drain_batch_size` <= 10000 AND `event_drain_delay_ms` BETWEEN 100 AND 3600000
         AND `event_max_attempt` <= 100 AND `ticket_lot_validity_days` <= 3650
         AND `ticket_expiry_batch_size` <= 10000 AND `ticket_max_grant_per_batch` <= 1000000
         AND `vote_max_tickets_per_request` <= 1000 AND `vote_max_votes_per_day` <= 10000
         AND `vote_max_tickets_per_day` <= 100000
         AND `vote_max_tickets_per_book_season` <= 1000000
         AND `vote_max_lots_per_spend` <= 10000
         AND `quest_heartbeat_interval_seconds` BETWEEN 10 AND 3600
         AND `quest_heartbeat_max_minutes_day` <= 1440
         AND `realm_change_cooldown_hours` <= 8760
         AND `season_close_drain_seconds` <= 86400
         AND `season_resume_delay_ms` BETWEEN 100 AND 3600000
         AND `season_review_window_hours` <= 720 AND `reward_claim_window_days` <= 365
         AND `job_lease_seconds` <= 86400 AND `job_batch_size` <= 100000),
    CONSTRAINT `chk_gamification_config_vote_limits` CHECK
        (`vote_max_tickets_per_day` >= `vote_max_tickets_per_request`
         AND `vote_max_tickets_per_book_season` >= `vote_max_tickets_per_request`),
    CONSTRAINT `chk_gamification_config_dependencies` CHECK
        ((`vote_enabled` = 0 OR `ticket_enabled` = 1)
         AND (`quest_enabled` = 0 OR `event_enabled` = 1)
         AND (`reward_enabled` = 0 OR `season_enabled` = 1)),
    CONSTRAINT `chk_gamification_config_maker_checker` CHECK
        (`high_risk` = 0 OR `approved_by` IS NULL OR `approved_by` <> `created_by`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Snapshot cấu hình runtime gamification, một ACTIVE và tối đa một SCHEDULED';

CREATE TABLE `gamification_runtime_config_audit`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `config_id`      BIGINT       NOT NULL,
    `event_type`     VARCHAR(32)  NOT NULL,
    `from_status`    VARCHAR(24)           DEFAULT NULL,
    `to_status`      VARCHAR(24)  NOT NULL,
    `operator_id`    BIGINT       NOT NULL,
    `reason`         VARCHAR(500) NOT NULL,
    `before_hash`    CHAR(64)              DEFAULT NULL,
    `after_hash`     CHAR(64)              DEFAULT NULL,
    `diff_json`      JSON                  DEFAULT NULL,
    `create_time`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_gamification_config_audit` (`config_id`, `id`)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Audit bất biến cho cấu hình runtime gamification';

INSERT INTO `gamification_runtime_config`
    (`id`, `revision_no`, `revision_code`, `status`, `activation_class`, `high_risk`,
     `policy_version`, `zone_id`,
     `event_enabled`, `event_drain_batch_size`, `event_drain_delay_ms`, `event_max_attempt`,
     `ticket_enabled`, `ticket_lot_validity_days`, `ticket_expiry_cron`,
     `ticket_expiry_batch_size`, `ticket_max_grant_per_batch`,
     `vote_enabled`, `vote_allow_crawled_books`, `vote_ip_hash_key_id`,
     `vote_max_tickets_per_request`, `vote_max_votes_per_day`, `vote_max_tickets_per_day`,
     `vote_max_tickets_per_book_season`, `vote_max_lots_per_spend`,
     `quest_enabled`, `quest_heartbeat_interval_seconds`, `quest_heartbeat_max_minutes_day`,
     `realm_enabled`, `realm_change_cooldown_hours`,
     `season_enabled`, `season_close_cron`, `season_close_drain_seconds`,
     `season_resume_delay_ms`, `season_review_window_hours`,
     `reward_enabled`, `reward_claim_window_days`, `reward_release_cron`,
     `job_lease_seconds`, `job_batch_size`, `config_hash`, `created_by`, `activated_by`,
     `change_reason`, `activated_at`)
VALUES
    (1, 1, 'v1-bootstrap', 'ACTIVE', 'IMMEDIATE', 0, 'v1', 'Asia/Ho_Chi_Minh',
     0, 200, 15000, 10,
     0, 60, '0 20 3 * * ?', 500, 1000,
     0, 0, 'v1', 10, 20, 50, 100, 50,
     0, 60, 180, 0, 24,
     0, '0 5 0 1 * ?', 60, 30000, 72,
     0, 7, '0 40 3 * * ?', 300, 500,
     '49463a16512a838d196424d4ef9b95c7676619aca3ebe7dc11a5cbc097e1a599', 1, 1,
     'Cấu hình bootstrap fail-closed', NOW(3));

INSERT INTO `gamification_runtime_config_audit`
    (`config_id`, `event_type`, `from_status`, `to_status`, `operator_id`, `reason`, `after_hash`)
VALUES
    (1, 'BOOTSTRAPPED', NULL, 'ACTIVE', 1, 'Cấu hình bootstrap fail-closed',
     '49463a16512a838d196424d4ef9b95c7676619aca3ebe7dc11a5cbc097e1a599');

ALTER TABLE `gamification_event`
    ADD COLUMN `runtime_config_revision` BIGINT NOT NULL DEFAULT 1 AFTER `policy_version`;
ALTER TABLE `monthly_ticket_lot`
    ADD COLUMN `runtime_config_revision` BIGINT NOT NULL DEFAULT 1 AFTER `policy_version`;
ALTER TABLE `monthly_ticket_vote`
    ADD COLUMN `runtime_config_revision` BIGINT NOT NULL DEFAULT 1 AFTER `policy_version`,
    ADD COLUMN `source_hash_key_id` VARCHAR(64) NOT NULL DEFAULT 'v1' AFTER `source_device_hash`;
ALTER TABLE `reading_heartbeat_receipt`
    ADD COLUMN `runtime_config_revision` BIGINT NOT NULL DEFAULT 1 AFTER `policy_version`;
ALTER TABLE `monthly_ticket_season`
    ADD COLUMN `runtime_config_revision` BIGINT NOT NULL DEFAULT 1 AFTER `policy_version`;
ALTER TABLE `reward_fund_campaign`
    ADD COLUMN `runtime_config_revision` BIGINT NOT NULL DEFAULT 1 AFTER `policy_version`;
ALTER TABLE `author_reward_allocation`
    ADD COLUMN `runtime_config_revision` BIGINT NOT NULL DEFAULT 1 AFTER `policy_version`,
    ADD COLUMN `release_eligible_at` DATETIME(3) DEFAULT NULL AFTER `posted_at`;

UPDATE `author_reward_allocation`
SET `release_eligible_at` = DATE_ADD(`posted_at`, INTERVAL 7 DAY)
WHERE `posted_at` IS NOT NULL AND `release_eligible_at` IS NULL;

ALTER TABLE `author_reward_allocation`
    ADD KEY `idx_reward_allocation_release_due` (`status`, `release_eligible_at`, `id`),
    ADD CONSTRAINT `chk_reward_allocation_release_due` CHECK
        (`status` NOT IN ('POSTED_PENDING', 'RELEASED') OR `release_eligible_at` IS NOT NULL);

DELIMITER //
CREATE TRIGGER `trg_gamification_runtime_config_guard`
    BEFORE UPDATE ON `gamification_runtime_config` FOR EACH ROW
BEGIN
    IF OLD.`status` <> 'DRAFT' AND (
        NOT (NEW.`revision_no` <=> OLD.`revision_no`)
        OR NOT (NEW.`revision_code` <=> OLD.`revision_code`)
        OR NOT (NEW.`source_revision_id` <=> OLD.`source_revision_id`)
        OR NOT (NEW.`source_hash` <=> OLD.`source_hash`)
        OR NOT (NEW.`activation_class` <=> OLD.`activation_class`)
        OR NOT (NEW.`high_risk` <=> OLD.`high_risk`)
        OR NOT (NEW.`policy_version` <=> OLD.`policy_version`)
        OR NOT (NEW.`zone_id` <=> OLD.`zone_id`)
        OR NOT (NEW.`event_enabled` <=> OLD.`event_enabled`)
        OR NOT (NEW.`event_drain_batch_size` <=> OLD.`event_drain_batch_size`)
        OR NOT (NEW.`event_drain_delay_ms` <=> OLD.`event_drain_delay_ms`)
        OR NOT (NEW.`event_max_attempt` <=> OLD.`event_max_attempt`)
        OR NOT (NEW.`ticket_enabled` <=> OLD.`ticket_enabled`)
        OR NOT (NEW.`ticket_lot_validity_days` <=> OLD.`ticket_lot_validity_days`)
        OR NOT (NEW.`ticket_expiry_cron` <=> OLD.`ticket_expiry_cron`)
        OR NOT (NEW.`ticket_expiry_batch_size` <=> OLD.`ticket_expiry_batch_size`)
        OR NOT (NEW.`ticket_max_grant_per_batch` <=> OLD.`ticket_max_grant_per_batch`)
        OR NOT (NEW.`vote_enabled` <=> OLD.`vote_enabled`)
        OR NOT (NEW.`vote_allow_crawled_books` <=> OLD.`vote_allow_crawled_books`)
        OR NOT (NEW.`vote_ip_hash_key_id` <=> OLD.`vote_ip_hash_key_id`)
        OR NOT (NEW.`vote_max_tickets_per_request` <=> OLD.`vote_max_tickets_per_request`)
        OR NOT (NEW.`vote_max_votes_per_day` <=> OLD.`vote_max_votes_per_day`)
        OR NOT (NEW.`vote_max_tickets_per_day` <=> OLD.`vote_max_tickets_per_day`)
        OR NOT (NEW.`vote_max_tickets_per_book_season` <=> OLD.`vote_max_tickets_per_book_season`)
        OR NOT (NEW.`vote_max_lots_per_spend` <=> OLD.`vote_max_lots_per_spend`)
        OR NOT (NEW.`quest_enabled` <=> OLD.`quest_enabled`)
        OR NOT (NEW.`quest_heartbeat_interval_seconds` <=> OLD.`quest_heartbeat_interval_seconds`)
        OR NOT (NEW.`quest_heartbeat_max_minutes_day` <=> OLD.`quest_heartbeat_max_minutes_day`)
        OR NOT (NEW.`realm_enabled` <=> OLD.`realm_enabled`)
        OR NOT (NEW.`realm_change_cooldown_hours` <=> OLD.`realm_change_cooldown_hours`)
        OR NOT (NEW.`season_enabled` <=> OLD.`season_enabled`)
        OR NOT (NEW.`season_close_cron` <=> OLD.`season_close_cron`)
        OR NOT (NEW.`season_close_drain_seconds` <=> OLD.`season_close_drain_seconds`)
        OR NOT (NEW.`season_resume_delay_ms` <=> OLD.`season_resume_delay_ms`)
        OR NOT (NEW.`season_review_window_hours` <=> OLD.`season_review_window_hours`)
        OR NOT (NEW.`reward_enabled` <=> OLD.`reward_enabled`)
        OR NOT (NEW.`reward_claim_window_days` <=> OLD.`reward_claim_window_days`)
        OR NOT (NEW.`reward_release_cron` <=> OLD.`reward_release_cron`)
        OR NOT (NEW.`job_lease_seconds` <=> OLD.`job_lease_seconds`)
        OR NOT (NEW.`job_batch_size` <=> OLD.`job_batch_size`)
        OR NOT (NEW.`config_hash` <=> OLD.`config_hash`)
        OR NOT (NEW.`created_by` <=> OLD.`created_by`)
    ) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'submitted gamification runtime config is immutable';
    END IF;
END//
CREATE TRIGGER `trg_gamification_runtime_config_no_delete`
    BEFORE DELETE ON `gamification_runtime_config` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification runtime config cannot be deleted';
END//
CREATE TRIGGER `trg_gamification_runtime_config_audit_no_update`
    BEFORE UPDATE ON `gamification_runtime_config_audit` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification runtime config audit is immutable';
END//
CREATE TRIGGER `trg_gamification_runtime_config_audit_no_delete`
    BEFORE DELETE ON `gamification_runtime_config_audit` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification runtime config audit is immutable';
END//
CREATE TRIGGER `trg_gamification_policy_bundle_audit_no_update`
    BEFORE UPDATE ON `gamification_policy_bundle_audit` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification policy audit is immutable';
END//
CREATE TRIGGER `trg_gamification_policy_bundle_audit_no_delete`
    BEFORE DELETE ON `gamification_policy_bundle_audit` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification policy audit is immutable';
END//
CREATE TRIGGER `trg_gamification_policy_bundle_guard`
    BEFORE UPDATE ON `gamification_policy_bundle` FOR EACH ROW
BEGIN
    IF NOT (NEW.`policy_version` <=> OLD.`policy_version`)
        OR NOT (NEW.`created_by` <=> OLD.`created_by`)
        OR NOT (NEW.`create_time` <=> OLD.`create_time`) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification policy identity is immutable';
    END IF;
    IF OLD.`status` <> 'DRAFT' AND NOT (NEW.`content_hash` <=> OLD.`content_hash`) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'submitted gamification policy is immutable';
    END IF;
END//
CREATE TRIGGER `trg_gamification_policy_bundle_no_delete`
    BEFORE DELETE ON `gamification_policy_bundle` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification policy bundle cannot be deleted';
END//
DROP TRIGGER IF EXISTS `trg_gamification_public_policy_guard`//
DROP TRIGGER IF EXISTS `trg_gamification_public_policy_bundle_insert`//
CREATE TRIGGER `trg_gamification_public_policy_bundle_insert`
    BEFORE INSERT ON `gamification_public_policy` FOR EACH ROW
BEGIN
    IF NOT EXISTS (SELECT 1 FROM `gamification_policy_bundle`
                   WHERE `policy_version` = NEW.`policy_version` AND `status` = 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'public policy requires a DRAFT policy bundle';
    END IF;
END//
CREATE TRIGGER `trg_gamification_public_policy_guard`
    BEFORE UPDATE ON `gamification_public_policy` FOR EACH ROW
BEGIN
    IF NOT (NEW.`policy_version` <=> OLD.`policy_version`) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'public policy version is immutable';
    END IF;
    IF OLD.`status` = 'DRAFT' AND NEW.`status` = 'DRAFT' THEN
        IF NOT EXISTS (SELECT 1 FROM `gamification_policy_bundle`
                       WHERE `policy_version` = OLD.`policy_version` AND `status` = 'DRAFT') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'submitted public policy is immutable';
        END IF;
        IF NEW.`published_by` IS NOT NULL OR NEW.`published_at` IS NOT NULL THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'DRAFT public policy cannot contain publisher data';
        END IF;
    ELSEIF OLD.`status` = 'DRAFT' AND NEW.`status` = 'PUBLISHED' THEN
        IF NOT EXISTS (SELECT 1 FROM `gamification_policy_bundle`
                       WHERE `policy_version` = OLD.`policy_version` AND `status` = 'APPROVED') THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'public policy bundle is not APPROVED';
        END IF;
        IF NOT (NEW.`title` <=> OLD.`title`)
            OR NOT (NEW.`content_text` <=> OLD.`content_text`) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'public policy content changed during publish';
        END IF;
    ELSEIF OLD.`status` = 'PUBLISHED' AND NEW.`status` = 'ARCHIVED' THEN
        IF NOT (NEW.`title` <=> OLD.`title`)
            OR NOT (NEW.`content_text` <=> OLD.`content_text`)
            OR NOT (NEW.`published_by` <=> OLD.`published_by`)
            OR NOT (NEW.`published_at` <=> OLD.`published_at`) THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'published public policy is immutable';
        END IF;
    ELSE
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'invalid gamification public policy status transition';
    END IF;
END//
CREATE TRIGGER `trg_quest_definition_version_no_update`
    BEFORE UPDATE ON `quest_definition` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = OLD.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'quest definition is immutable; create a new policy version';
    END IF;
END//
CREATE TRIGGER `trg_quest_definition_version_no_delete`
    BEFORE DELETE ON `quest_definition` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = OLD.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'quest definition cannot be deleted';
    END IF;
END//
CREATE TRIGGER `trg_quest_reward_version_no_update`
    BEFORE UPDATE ON `quest_reward` FOR EACH ROW
BEGIN
    IF (OLD.`campaign_code` = 'DEFAULT' AND EXISTS (
            SELECT 1 FROM `gamification_policy_bundle`
            WHERE `policy_version` = OLD.`policy_version` AND `status` <> 'DRAFT'))
       OR (NEW.`campaign_code` = 'DEFAULT' AND EXISTS (
            SELECT 1 FROM `gamification_policy_bundle`
            WHERE `policy_version` = NEW.`policy_version` AND `status` <> 'DRAFT')) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'quest reward is immutable; create a new policy version';
    END IF;
END//
CREATE TRIGGER `trg_quest_reward_version_no_delete`
    BEFORE DELETE ON `quest_reward` FOR EACH ROW
BEGIN
    IF OLD.`campaign_code` = 'DEFAULT' AND EXISTS (
            SELECT 1 FROM `gamification_policy_bundle`
            WHERE `policy_version` = OLD.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'quest reward cannot be deleted';
    END IF;
END//
CREATE TRIGGER `trg_realm_catalog_version_no_update`
    BEFORE UPDATE ON `realm_catalog` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = OLD.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'realm catalog is immutable; create a new policy version';
    END IF;
END//
CREATE TRIGGER `trg_realm_catalog_version_no_delete`
    BEFORE DELETE ON `realm_catalog` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = OLD.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'realm catalog cannot be deleted';
    END IF;
END//

DROP TRIGGER IF EXISTS `trg_level_rule_no_update`//
DROP TRIGGER IF EXISTS `trg_level_rule_no_delete`//
DROP TRIGGER IF EXISTS `trg_level_reward_policy_no_update`//
DROP TRIGGER IF EXISTS `trg_level_reward_policy_no_delete`//
DROP TRIGGER IF EXISTS `trg_gamification_abuse_policy_no_update`//
DROP TRIGGER IF EXISTS `trg_gamification_abuse_policy_no_delete`//
DROP TRIGGER IF EXISTS `trg_gamification_abuse_rule_no_update`//
DROP TRIGGER IF EXISTS `trg_gamification_abuse_rule_no_delete`//

CREATE TRIGGER `trg_level_rule_policy_no_insert`
    BEFORE INSERT ON `level_rule` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = NEW.`rule_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'level rule policy is immutable';
    END IF;
END//
CREATE TRIGGER `trg_level_rule_no_update`
    BEFORE UPDATE ON `level_rule` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = OLD.`rule_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'level rule policy is immutable';
    END IF;
END//
CREATE TRIGGER `trg_level_rule_no_delete`
    BEFORE DELETE ON `level_rule` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = OLD.`rule_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'level rule policy is immutable';
    END IF;
END//

CREATE TRIGGER `trg_level_reward_policy_no_insert`
    BEFORE INSERT ON `level_reward_policy` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = NEW.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'level reward policy is immutable';
    END IF;
END//
CREATE TRIGGER `trg_level_reward_policy_no_update`
    BEFORE UPDATE ON `level_reward_policy` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = OLD.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'level reward policy is immutable';
    END IF;
END//
CREATE TRIGGER `trg_level_reward_policy_no_delete`
    BEFORE DELETE ON `level_reward_policy` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = OLD.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'level reward policy is immutable';
    END IF;
END//

CREATE TRIGGER `trg_quest_definition_policy_no_insert`
    BEFORE INSERT ON `quest_definition` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = NEW.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'quest definition policy is immutable';
    END IF;
END//
CREATE TRIGGER `trg_quest_reward_policy_no_insert`
    BEFORE INSERT ON `quest_reward` FOR EACH ROW
BEGIN
    IF NEW.`campaign_code` = 'DEFAULT' AND EXISTS (
            SELECT 1 FROM `gamification_policy_bundle`
            WHERE `policy_version` = NEW.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'quest reward policy is immutable';
    END IF;
END//
CREATE TRIGGER `trg_realm_catalog_policy_no_insert`
    BEFORE INSERT ON `realm_catalog` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = NEW.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'realm catalog policy is immutable';
    END IF;
END//

CREATE TRIGGER `trg_gamification_abuse_policy_no_insert`
    BEFORE INSERT ON `gamification_abuse_policy` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = NEW.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'abuse policy is immutable';
    END IF;
END//
CREATE TRIGGER `trg_gamification_abuse_policy_no_update`
    BEFORE UPDATE ON `gamification_abuse_policy` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = OLD.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'abuse policy is immutable';
    END IF;
END//
CREATE TRIGGER `trg_gamification_abuse_policy_no_delete`
    BEFORE DELETE ON `gamification_abuse_policy` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = OLD.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'abuse policy is immutable';
    END IF;
END//
CREATE TRIGGER `trg_gamification_abuse_rule_no_insert`
    BEFORE INSERT ON `gamification_abuse_rule` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = NEW.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'abuse rule policy is immutable';
    END IF;
END//
CREATE TRIGGER `trg_gamification_abuse_rule_no_update`
    BEFORE UPDATE ON `gamification_abuse_rule` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = OLD.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'abuse rule policy is immutable';
    END IF;
END//
CREATE TRIGGER `trg_gamification_abuse_rule_no_delete`
    BEFORE DELETE ON `gamification_abuse_rule` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = OLD.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'abuse rule policy is immutable';
    END IF;
END//
DELIMITER ;

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Xem cấu hình gamification', NULL,
       'novel:gamification:settings:view', 2, NULL, 7, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:gamification:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:gamification:settings:view');

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Sửa cấu hình gamification', NULL,
       'novel:gamification:settings:edit', 2, NULL, 8, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:gamification:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:gamification:settings:edit');

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Duyệt cấu hình gamification', NULL,
       'novel:gamification:settings:approve', 2, NULL, 9, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:gamification:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:gamification:settings:approve');

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Kích hoạt cấu hình gamification', NULL,
       'novel:gamification:settings:activate', 2, NULL, 10, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:gamification:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:gamification:settings:activate');

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Xem policy gamification', NULL,
       'novel:gamification:policy:view', 2, NULL, 11, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:gamification:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:gamification:policy:view');

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Sửa policy gamification', NULL,
       'novel:gamification:policy:edit', 2, NULL, 12, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:gamification:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:gamification:policy:edit');

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Duyệt policy gamification', NULL,
       'novel:gamification:policy:approve', 2, NULL, 13, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:gamification:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:gamification:policy:approve');

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Phát hành policy gamification', NULL,
       'novel:gamification:policy:publish', 2, NULL, 14, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:gamification:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:gamification:policy:publish');

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT role.role_id, menu.menu_id
FROM `sys_role` role
JOIN `sys_menu` menu ON menu.perms LIKE 'novel:gamification:settings:%'
                       OR menu.perms LIKE 'novel:gamification:policy:%'
WHERE role.role_sign = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` relation
      WHERE relation.role_id = role.role_id AND relation.menu_id = menu.menu_id
  );
