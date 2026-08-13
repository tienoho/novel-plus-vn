-- Policy thưởng level được version hóa; consumer cấp Đuốc qua lot/ledger hiện có.

CREATE TABLE `level_reward_policy`
(
    `id`                   BIGINT      NOT NULL AUTO_INCREMENT,
    `policy_version`       VARCHAR(32) NOT NULL,
    `level`                INT         NOT NULL,
    `ticket_amount`        BIGINT      NOT NULL,
    `ticket_validity_days` INT         NOT NULL,
    `create_time`          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_level_reward_policy` (`policy_version`, `level`),
    CONSTRAINT `chk_level_reward_policy_level` CHECK (`level` > 1),
    CONSTRAINT `chk_level_reward_policy_amount` CHECK (`ticket_amount` > 0),
    CONSTRAINT `chk_level_reward_policy_validity` CHECK (`ticket_validity_days` BETWEEN 1 AND 3650)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Cấu hình thưởng Đuốc khi đạt level; đổi chính sách bằng version mới';

CREATE TABLE `level_reward_grant`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `event_id`        BIGINT       NOT NULL,
    `user_id`         BIGINT       NOT NULL,
    `level`           INT          NOT NULL,
    `policy_version`  VARCHAR(32)  NOT NULL,
    `ticket_amount`   BIGINT       NOT NULL,
    `ticket_ledger_id` BIGINT      NOT NULL,
    `idempotency_key` VARCHAR(128) NOT NULL,
    `create_time`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_level_reward_grant_scope` (`user_id`, `level`, `policy_version`),
    UNIQUE KEY `uk_level_reward_grant_event` (`event_id`),
    UNIQUE KEY `uk_level_reward_grant_key` (`idempotency_key`),
    CONSTRAINT `chk_level_reward_grant_level` CHECK (`level` > 1),
    CONSTRAINT `chk_level_reward_grant_amount` CHECK (`ticket_amount` > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Kết quả cấp thưởng level bất biến và idempotent';

DELIMITER //
CREATE TRIGGER `trg_level_reward_policy_no_update`
    BEFORE UPDATE ON `level_reward_policy` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'level_reward_policy is immutable';
END//
CREATE TRIGGER `trg_level_reward_policy_no_delete`
    BEFORE DELETE ON `level_reward_policy` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'level_reward_policy is immutable';
END//
CREATE TRIGGER `trg_level_reward_grant_no_update`
    BEFORE UPDATE ON `level_reward_grant` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'level_reward_grant is immutable';
END//
CREATE TRIGGER `trg_level_reward_grant_no_delete`
    BEFORE DELETE ON `level_reward_grant` FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'level_reward_grant is immutable';
END//
DELIMITER ;
