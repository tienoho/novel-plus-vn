-- Khóa các invariant P1 của cấu hình động Gamification mà không sửa migration lịch sử.

SET NAMES utf8mb4;

ALTER TABLE `gamification_public_policy`
    DROP INDEX `uk_gamification_public_policy_published`;

ALTER TABLE `gamification_runtime_config`
    DROP CHECK `chk_gamification_config_resource_bounds`,
    ADD CONSTRAINT `chk_gamification_config_resource_bounds` CHECK
        (`event_drain_batch_size` <= 10000
         AND `event_drain_delay_ms` BETWEEN 100 AND 3600000
         AND `event_drain_batch_size` * 1000 <= `event_drain_delay_ms` * 2000
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
         AND `job_lease_seconds` <= 86400 AND `job_batch_size` <= 10000);

DELIMITER //
DROP TRIGGER IF EXISTS `trg_gamification_runtime_config_status_guard`//
CREATE TRIGGER `trg_gamification_runtime_config_status_guard`
    BEFORE UPDATE ON `gamification_runtime_config` FOR EACH ROW
BEGIN
    IF NOT (NEW.`status` <=> OLD.`status`) AND NOT (
        (OLD.`status` = 'DRAFT' AND NEW.`status` = 'PENDING_APPROVAL')
        OR (OLD.`status` = 'PENDING_APPROVAL' AND NEW.`status` IN ('APPROVED', 'REJECTED'))
        OR (OLD.`status` = 'APPROVED' AND NEW.`status` = 'SCHEDULED')
        OR (OLD.`status` = 'SCHEDULED' AND NEW.`status` IN ('ACTIVE', 'CANCELLED'))
        OR (OLD.`status` = 'ACTIVE' AND NEW.`status` = 'ARCHIVED')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'invalid gamification runtime config status transition';
    END IF;
END//

DROP TRIGGER IF EXISTS `trg_gamification_policy_bundle_status_guard`//
CREATE TRIGGER `trg_gamification_policy_bundle_status_guard`
    BEFORE UPDATE ON `gamification_policy_bundle` FOR EACH ROW
BEGIN
    IF NOT (NEW.`status` <=> OLD.`status`) AND NOT (
        (OLD.`status` = 'DRAFT' AND NEW.`status` = 'PENDING_APPROVAL')
        OR (OLD.`status` = 'PENDING_APPROVAL' AND NEW.`status` IN ('APPROVED', 'REJECTED'))
        OR (OLD.`status` = 'APPROVED' AND NEW.`status` = 'PUBLISHED')
        OR (OLD.`status` = 'PUBLISHED' AND NEW.`status` = 'ARCHIVED')
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'invalid gamification policy status transition';
    END IF;
END//

DROP TRIGGER IF EXISTS `trg_gamification_public_policy_guard`//
INSERT INTO `gamification_public_policy_audit`
    (`policy_id`, `event_type`, `from_status`, `to_status`, `operator_id`)
SELECT policy.`id`, 'RESTORED', 'ARCHIVED', 'PUBLISHED', policy.`published_by`
FROM `gamification_public_policy` policy
JOIN `gamification_policy_bundle` bundle
  ON bundle.`policy_version` = policy.`policy_version`
WHERE policy.`status` = 'ARCHIVED' AND bundle.`status` = 'PUBLISHED'//

UPDATE `gamification_public_policy` policy
JOIN `gamification_policy_bundle` bundle
  ON bundle.`policy_version` = policy.`policy_version`
SET policy.`status` = 'PUBLISHED', policy.`archived_at` = NULL,
    policy.`version` = policy.`version` + 1
WHERE policy.`status` = 'ARCHIVED' AND bundle.`status` = 'PUBLISHED'//

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
    ELSE
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'invalid gamification public policy status transition';
    END IF;
END//

DROP TRIGGER IF EXISTS `trg_level_rule_target_policy_guard`//
CREATE TRIGGER `trg_level_rule_target_policy_guard`
    BEFORE UPDATE ON `level_rule` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = NEW.`rule_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'level rule target policy is immutable';
    END IF;
END//

DROP TRIGGER IF EXISTS `trg_level_reward_target_policy_guard`//
CREATE TRIGGER `trg_level_reward_target_policy_guard`
    BEFORE UPDATE ON `level_reward_policy` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = NEW.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'level reward target policy is immutable';
    END IF;
END//

DROP TRIGGER IF EXISTS `trg_quest_definition_target_policy_guard`//
CREATE TRIGGER `trg_quest_definition_target_policy_guard`
    BEFORE UPDATE ON `quest_definition` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = NEW.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'quest definition target policy is immutable';
    END IF;
END//

DROP TRIGGER IF EXISTS `trg_realm_catalog_target_policy_guard`//
CREATE TRIGGER `trg_realm_catalog_target_policy_guard`
    BEFORE UPDATE ON `realm_catalog` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = NEW.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'realm catalog target policy is immutable';
    END IF;
END//

DROP TRIGGER IF EXISTS `trg_gamification_abuse_policy_target_guard`//
CREATE TRIGGER `trg_gamification_abuse_policy_target_guard`
    BEFORE UPDATE ON `gamification_abuse_policy` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = NEW.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'abuse target policy is immutable';
    END IF;
END//

DROP TRIGGER IF EXISTS `trg_gamification_abuse_rule_target_guard`//
CREATE TRIGGER `trg_gamification_abuse_rule_target_guard`
    BEFORE UPDATE ON `gamification_abuse_rule` FOR EACH ROW
BEGIN
    IF EXISTS (SELECT 1 FROM `gamification_policy_bundle`
               WHERE `policy_version` = NEW.`policy_version` AND `status` <> 'DRAFT') THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'abuse rule target policy is immutable';
    END IF;
END//
DELIMITER ;
