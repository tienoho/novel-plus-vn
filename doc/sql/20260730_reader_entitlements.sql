-- Vé đọc và quyền đọc chương: sổ cái bất biến, lot FIFO và entitlement idempotent.
--
-- Vé đọc là quyền sử dụng nội bộ, không phải Xu và không chuyển nhượng. Mỗi lần cấp tạo một
-- lot có hạn; mỗi lần mở chương tiêu đúng một Vé đọc và tạo entitlement vĩnh viễn cho chương.
-- Không dùng FOREIGN KEY để giữ cùng quy ước vận hành với các sổ cái hiện có trong hệ thống.

SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `reading_ticket_account`
(
    `id`                BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`           BIGINT      NOT NULL,
    `available_balance` BIGINT      NOT NULL DEFAULT 0,
    `lifetime_granted`  BIGINT      NOT NULL DEFAULT 0,
    `lifetime_spent`    BIGINT      NOT NULL DEFAULT 0,
    `lifetime_expired`  BIGINT      NOT NULL DEFAULT 0,
    `status`            VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    `version`           BIGINT      NOT NULL DEFAULT 0,
    `create_time`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rt_account_user` (`user_id`),
    CONSTRAINT `chk_rt_account_balance` CHECK (`available_balance` >= 0),
    CONSTRAINT `chk_rt_account_lifetime` CHECK (`lifetime_granted` >= 0
        AND `lifetime_spent` >= 0 AND `lifetime_expired` >= 0),
    CONSTRAINT `chk_rt_account_status` CHECK (`status` IN ('ACTIVE', 'FROZEN'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Projection số dư Vé đọc; lot và sổ cái là nguồn kiểm toán';

CREATE TABLE IF NOT EXISTS `reading_ticket_ledger`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `entry_no`        VARCHAR(64)  NOT NULL,
    `user_id`         BIGINT       NOT NULL,
    `entry_type`      VARCHAR(24)  NOT NULL,
    `amount`          BIGINT       NOT NULL COMMENT 'Dương khi cấp, âm khi tiêu, hết hạn hoặc thu hồi',
    `balance_after`   BIGINT       NOT NULL,
    `business_type`   VARCHAR(32)  NOT NULL,
    `business_id`     VARCHAR(128) NOT NULL,
    `idempotency_key` VARCHAR(128) NOT NULL,
    `request_hash`    CHAR(64)     NOT NULL,
    `operator_type`   VARCHAR(16)  NOT NULL DEFAULT 'SYSTEM',
    `operator_id`     BIGINT                DEFAULT NULL,
    `reason`          VARCHAR(255)          DEFAULT NULL,
    `policy_version`  VARCHAR(32)  NOT NULL,
    `create_time`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rt_ledger_entry_no` (`entry_no`),
    UNIQUE KEY `uk_rt_ledger_idempotency` (`idempotency_key`),
    KEY `idx_rt_ledger_user` (`user_id`, `id`),
    KEY `idx_rt_ledger_business` (`business_type`, `business_id`),
    CONSTRAINT `chk_rt_ledger_amount` CHECK (`amount` <> 0),
    CONSTRAINT `chk_rt_ledger_balance` CHECK (`balance_after` >= 0),
    CONSTRAINT `chk_rt_ledger_type` CHECK (`entry_type` IN
        ('GRANT', 'SPEND', 'EXPIRE', 'REVOKE', 'ADJUST', 'REVERSAL')),
    CONSTRAINT `chk_rt_ledger_sign` CHECK (
        (`entry_type` = 'GRANT' AND `amount` > 0)
            OR (`entry_type` IN ('SPEND', 'EXPIRE', 'REVOKE') AND `amount` < 0)
            OR `entry_type` IN ('ADJUST', 'REVERSAL')),
    CONSTRAINT `chk_rt_ledger_operator_type` CHECK (`operator_type` IN ('SYSTEM', 'ADMIN', 'USER')),
    CONSTRAINT `chk_rt_ledger_operator` CHECK (`operator_type` <> 'ADMIN'
        OR (`operator_id` IS NOT NULL AND `reason` IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Bút toán Vé đọc bất biến; sửa sai bằng bút toán đảo';

CREATE TABLE IF NOT EXISTS `reading_ticket_lot`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`          BIGINT       NOT NULL,
    `source_type`      VARCHAR(32)  NOT NULL,
    `source_ref`       VARCHAR(96)  NOT NULL,
    `granted_amount`   BIGINT       NOT NULL,
    `remaining_amount` BIGINT       NOT NULL,
    `grant_ledger_id`  BIGINT       NOT NULL,
    `effective_at`     DATETIME(3)  NOT NULL,
    `expire_at`        DATETIME(3)  NOT NULL,
    `status`           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    `closed_at`        DATETIME(3)           DEFAULT NULL,
    `policy_version`   VARCHAR(32)  NOT NULL,
    `version`          BIGINT       NOT NULL DEFAULT 0,
    `create_time`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rt_lot_grant_ledger` (`grant_ledger_id`),
    KEY `idx_rt_lot_fifo` (`user_id`, `status`, `expire_at`, `id`),
    KEY `idx_rt_lot_expiry` (`status`, `expire_at`, `id`),
    KEY `idx_rt_lot_source` (`source_type`, `source_ref`),
    CONSTRAINT `chk_rt_lot_amounts` CHECK (`granted_amount` > 0
        AND `remaining_amount` >= 0 AND `remaining_amount` <= `granted_amount`),
    CONSTRAINT `chk_rt_lot_window` CHECK (`expire_at` > `effective_at`),
    CONSTRAINT `chk_rt_lot_status` CHECK (`status` IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT `chk_rt_lot_closed` CHECK (
        (`status` = 'ACTIVE' AND `remaining_amount` > 0 AND `closed_at` IS NULL)
            OR (`status` IN ('EXHAUSTED', 'EXPIRED', 'REVOKED')
                AND `remaining_amount` = 0 AND `closed_at` IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Lô Vé đọc tiêu theo FIFO, ưu tiên lô hết hạn sớm';

CREATE TABLE IF NOT EXISTS `reading_ticket_lot_allocation`
(
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT,
    `ledger_id`           BIGINT      NOT NULL,
    `lot_id`              BIGINT      NOT NULL,
    `amount`              BIGINT      NOT NULL,
    `lot_remaining_after` BIGINT      NOT NULL,
    `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_rt_alloc_ledger_lot` (`ledger_id`, `lot_id`),
    KEY `idx_rt_alloc_lot` (`lot_id`, `id`),
    CONSTRAINT `chk_rt_alloc_amount` CHECK (`amount` > 0),
    CONSTRAINT `chk_rt_alloc_remaining` CHECK (`lot_remaining_after` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Phân bổ bút toán tiêu Vé đọc vào lot nguồn';

CREATE TABLE IF NOT EXISTS `chapter_entitlement`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`         BIGINT       NOT NULL,
    `book_id`         BIGINT       NOT NULL,
    `book_index_id`   BIGINT       NOT NULL,
    `source_type`     VARCHAR(32)  NOT NULL,
    `source_id`       VARCHAR(128) NOT NULL,
    `valid_from`      DATETIME(3)  NOT NULL,
    `valid_until`     DATETIME(3)           DEFAULT NULL,
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    `idempotency_key` VARCHAR(128) NOT NULL,
    `policy_version`  VARCHAR(32)  NOT NULL,
    `active_slot`     TINYINT GENERATED ALWAYS AS
        (CASE WHEN `status` = 'ACTIVE' THEN 1 ELSE NULL END) STORED,
    `create_time`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_chapter_entitlement_idempotency` (`idempotency_key`),
    UNIQUE KEY `uk_chapter_entitlement_active` (`user_id`, `book_index_id`, `active_slot`),
    KEY `idx_chapter_entitlement_book_user` (`book_id`, `user_id`, `status`),
    KEY `idx_chapter_entitlement_source` (`source_type`, `source_id`),
    CONSTRAINT `chk_chapter_entitlement_window` CHECK
        (`valid_until` IS NULL OR `valid_until` > `valid_from`),
    CONSTRAINT `chk_chapter_entitlement_status` CHECK (`status` IN ('ACTIVE', 'EXPIRED', 'REVOKED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Quyền đọc chương độc lập với lịch sử mua bằng Xu';

DROP TRIGGER IF EXISTS `trg_reading_ticket_ledger_no_update`;
DROP TRIGGER IF EXISTS `trg_reading_ticket_ledger_no_delete`;
DROP TRIGGER IF EXISTS `trg_reading_ticket_alloc_no_update`;
DROP TRIGGER IF EXISTS `trg_reading_ticket_alloc_no_delete`;
DROP TRIGGER IF EXISTS `trg_reading_ticket_lot_immutable_columns`;
DROP TRIGGER IF EXISTS `trg_reading_ticket_lot_no_delete`;
DROP TRIGGER IF EXISTS `trg_chapter_entitlement_immutable_columns`;
DROP TRIGGER IF EXISTS `trg_chapter_entitlement_no_delete`;

DELIMITER $$
CREATE TRIGGER `trg_reading_ticket_ledger_no_update`
    BEFORE UPDATE ON `reading_ticket_ledger`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reading_ticket_ledger is immutable';
END$$

CREATE TRIGGER `trg_reading_ticket_ledger_no_delete`
    BEFORE DELETE ON `reading_ticket_ledger`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reading_ticket_ledger is immutable';
END$$

CREATE TRIGGER `trg_reading_ticket_alloc_no_update`
    BEFORE UPDATE ON `reading_ticket_lot_allocation`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reading_ticket_lot_allocation is immutable';
END$$

CREATE TRIGGER `trg_reading_ticket_alloc_no_delete`
    BEFORE DELETE ON `reading_ticket_lot_allocation`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reading_ticket_lot_allocation is immutable';
END$$

CREATE TRIGGER `trg_reading_ticket_lot_immutable_columns`
    BEFORE UPDATE ON `reading_ticket_lot`
    FOR EACH ROW
BEGIN
    IF NOT (OLD.`user_id` <=> NEW.`user_id`)
        OR NOT (OLD.`source_type` <=> NEW.`source_type`)
        OR NOT (OLD.`source_ref` <=> NEW.`source_ref`)
        OR NOT (OLD.`granted_amount` <=> NEW.`granted_amount`)
        OR NOT (OLD.`grant_ledger_id` <=> NEW.`grant_ledger_id`)
        OR NOT (OLD.`effective_at` <=> NEW.`effective_at`)
        OR NOT (OLD.`expire_at` <=> NEW.`expire_at`)
        OR NOT (OLD.`policy_version` <=> NEW.`policy_version`)
        OR NOT (OLD.`create_time` <=> NEW.`create_time`) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reading_ticket_lot identity is immutable';
    END IF;
END$$

CREATE TRIGGER `trg_reading_ticket_lot_no_delete`
    BEFORE DELETE ON `reading_ticket_lot`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reading_ticket_lot cannot be deleted';
END$$

CREATE TRIGGER `trg_chapter_entitlement_immutable_columns`
    BEFORE UPDATE ON `chapter_entitlement`
    FOR EACH ROW
BEGIN
    IF NOT (OLD.`user_id` <=> NEW.`user_id`)
        OR NOT (OLD.`book_id` <=> NEW.`book_id`)
        OR NOT (OLD.`book_index_id` <=> NEW.`book_index_id`)
        OR NOT (OLD.`source_type` <=> NEW.`source_type`)
        OR NOT (OLD.`source_id` <=> NEW.`source_id`)
        OR NOT (OLD.`valid_from` <=> NEW.`valid_from`)
        OR NOT (OLD.`idempotency_key` <=> NEW.`idempotency_key`)
        OR NOT (OLD.`policy_version` <=> NEW.`policy_version`)
        OR NOT (OLD.`create_time` <=> NEW.`create_time`) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'chapter_entitlement identity is immutable';
    END IF;
    IF OLD.`status` <> 'ACTIVE' AND NEW.`status` = 'ACTIVE' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'chapter_entitlement cannot be reactivated';
    END IF;
END$$

CREATE TRIGGER `trg_chapter_entitlement_no_delete`
    BEFORE DELETE ON `chapter_entitlement`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'chapter_entitlement cannot be deleted';
END$$
DELIMITER ;
