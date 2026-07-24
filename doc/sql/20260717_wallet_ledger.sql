-- Sổ cái kép cho Xu, ví độc giả và ví doanh thu tác giả.
-- user.account_balance được giữ làm projection tương thích; wallet_account là nguồn số dư có kiểm toán.
SET NAMES utf8mb4;

CREATE TABLE IF NOT EXISTS `platform_migration_history`
(
    `migration_key` varchar(100) NOT NULL,
    `applied_at`    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`migration_key`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Lịch sử migration dữ liệu cần chạy đúng một lần';

CREATE TABLE IF NOT EXISTS `wallet_account`
(
    `id`                bigint(20)   NOT NULL AUTO_INCREMENT,
    `owner_type`        varchar(16)  NOT NULL COMMENT 'SYSTEM, USER hoặc AUTHOR',
    `owner_id`          bigint(20)   NOT NULL,
    `account_type`      varchar(32)  NOT NULL COMMENT 'SYSTEM_ISSUANCE, PLATFORM_REVENUE, READER_XU hoặc AUTHOR_REVENUE_XU',
    `currency`          varchar(8)   NOT NULL DEFAULT 'XU',
    `available_balance` bigint(20)   NOT NULL DEFAULT 0,
    `pending_balance`   bigint(20)   NOT NULL DEFAULT 0,
    `version`           bigint(20)   NOT NULL DEFAULT 0,
    `status`            varchar(16)  NOT NULL DEFAULT 'ACTIVE',
    `create_time`       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`       datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wallet_owner_account_currency` (`owner_type`, `owner_id`, `account_type`, `currency`),
    KEY `idx_wallet_owner` (`owner_type`, `owner_id`),
    CONSTRAINT `chk_wallet_available_balance` CHECK (`owner_type` = 'SYSTEM' OR `available_balance` >= 0),
    CONSTRAINT `chk_wallet_pending_balance` CHECK (`pending_balance` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Số dư hiện tại của từng ví';

CREATE TABLE IF NOT EXISTS `ledger_transaction`
(
    `id`                      bigint(20)   NOT NULL AUTO_INCREMENT,
    `transaction_no`          varchar(64)  NOT NULL,
    `idempotency_key`         varchar(128) NOT NULL,
    `request_hash`            char(64)     NOT NULL,
    `business_type`           varchar(32)  NOT NULL,
    `business_id`             varchar(64)  NOT NULL,
    `currency`                varchar(8)   NOT NULL DEFAULT 'XU',
    `total_amount`            bigint(20)   NOT NULL,
    `reversal_of_transaction_id` bigint(20) DEFAULT NULL,
    `description`             varchar(255) DEFAULT NULL,
    `create_time`             datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_ledger_transaction_no` (`transaction_no`),
    UNIQUE KEY `uk_ledger_idempotency_key` (`idempotency_key`),
    UNIQUE KEY `uk_ledger_single_reversal` (`reversal_of_transaction_id`),
    KEY `idx_ledger_business` (`business_type`, `business_id`),
    CONSTRAINT `chk_ledger_total_amount` CHECK (`total_amount` > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Giao dịch tài chính bất biến';

CREATE TABLE IF NOT EXISTS `wallet_entry`
(
    `id`                    bigint(20)  NOT NULL AUTO_INCREMENT,
    `ledger_transaction_id` bigint(20)  NOT NULL,
    `wallet_account_id`     bigint(20)  NOT NULL,
    `amount`                bigint(20)  NOT NULL COMMENT 'Số dương ghi Có, số âm ghi Nợ',
    `balance_after`         bigint(20)  NOT NULL,
    `create_time`           datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_wallet_entry_transaction_account` (`ledger_transaction_id`, `wallet_account_id`),
    KEY `idx_wallet_entry_account_time` (`wallet_account_id`, `create_time`),
    CONSTRAINT `chk_wallet_entry_amount` CHECK (`amount` <> 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Bút toán bất biến của sổ cái kép';

INSERT IGNORE INTO `wallet_account`
    (`owner_type`, `owner_id`, `account_type`, `currency`, `available_balance`, `pending_balance`, `version`, `status`)
VALUES ('SYSTEM', 0, 'SYSTEM_ISSUANCE', 'XU', 0, 0, 0, 'ACTIVE'),
       ('SYSTEM', 0, 'PLATFORM_REVENUE', 'XU', 0, 0, 0, 'ACTIVE'),
       ('SYSTEM', 0, 'PAYOUT_CLEARING', 'XU', 0, 0, 0, 'ACTIVE');

SET @wallet_opening_migration := _utf8mb4'20260717_wallet_ledger_opening_v1' COLLATE utf8mb4_unicode_ci;
SET @run_wallet_opening := (
    SELECT COUNT(*) = 0
    FROM `platform_migration_history`
    WHERE `migration_key` = @wallet_opening_migration
);

INSERT IGNORE INTO `wallet_account`
    (`owner_type`, `owner_id`, `account_type`, `currency`, `available_balance`, `pending_balance`, `version`, `status`)
SELECT 'USER', `id`, 'READER_XU', 'XU', `account_balance`, 0, 0, 'ACTIVE'
FROM `user`
WHERE @run_wallet_opening = 1;

INSERT IGNORE INTO `wallet_account`
    (`owner_type`, `owner_id`, `account_type`, `currency`, `available_balance`, `pending_balance`, `version`, `status`)
SELECT 'AUTHOR', `id`, 'AUTHOR_REVENUE_XU', 'XU', 0, 0, 0, 'ACTIVE'
FROM `author`
WHERE @run_wallet_opening = 1;

SET @opening_total := (
    SELECT COALESCE(SUM(`account_balance`), 0)
    FROM `user`
    WHERE @run_wallet_opening = 1
      AND `account_balance` > 0
);

INSERT IGNORE INTO `ledger_transaction`
    (`transaction_no`, `idempotency_key`, `request_hash`, `business_type`, `business_id`, `currency`,
     `total_amount`, `description`)
SELECT 'OPENING-20260717', @wallet_opening_migration, SHA2(@wallet_opening_migration, 256),
       'OPENING_BALANCE', '20260717', 'XU', @opening_total, 'Số dư độc giả đầu kỳ khi khởi tạo sổ cái'
WHERE @run_wallet_opening = 1
  AND @opening_total > 0;

SET @opening_transaction_id := (
    SELECT `id`
    FROM `ledger_transaction`
    WHERE `idempotency_key` = @wallet_opening_migration
    LIMIT 1
);

INSERT IGNORE INTO `wallet_entry`
    (`ledger_transaction_id`, `wallet_account_id`, `amount`, `balance_after`)
SELECT @opening_transaction_id, wa.`id`, u.`account_balance`, u.`account_balance`
FROM `user` u
JOIN `wallet_account` wa
  ON wa.`owner_type` = 'USER'
 AND wa.`owner_id` = u.`id`
 AND wa.`account_type` = 'READER_XU'
 AND wa.`currency` = 'XU'
WHERE @run_wallet_opening = 1
  AND @opening_transaction_id IS NOT NULL
  AND u.`account_balance` > 0;

UPDATE `wallet_account`
SET `available_balance` = -@opening_total,
    `version` = `version` + 1
WHERE @run_wallet_opening = 1
  AND @opening_transaction_id IS NOT NULL
  AND `owner_type` = 'SYSTEM'
  AND `owner_id` = 0
  AND `account_type` = 'SYSTEM_ISSUANCE'
  AND `currency` = 'XU';

INSERT IGNORE INTO `wallet_entry`
    (`ledger_transaction_id`, `wallet_account_id`, `amount`, `balance_after`)
SELECT @opening_transaction_id, wa.`id`, -@opening_total, -@opening_total
FROM `wallet_account` wa
WHERE @run_wallet_opening = 1
  AND @opening_transaction_id IS NOT NULL
  AND wa.`owner_type` = 'SYSTEM'
  AND wa.`owner_id` = 0
  AND wa.`account_type` = 'SYSTEM_ISSUANCE'
  AND wa.`currency` = 'XU';

INSERT IGNORE INTO `platform_migration_history` (`migration_key`)
SELECT @wallet_opening_migration
WHERE @run_wallet_opening = 1;

DROP TRIGGER IF EXISTS `trg_ledger_transaction_no_update`;
DROP TRIGGER IF EXISTS `trg_ledger_transaction_no_delete`;
DROP TRIGGER IF EXISTS `trg_wallet_entry_no_update`;
DROP TRIGGER IF EXISTS `trg_wallet_entry_no_delete`;

DELIMITER $$
CREATE TRIGGER `trg_ledger_transaction_no_update`
    BEFORE UPDATE ON `ledger_transaction`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ledger_transaction is immutable';
END$$

CREATE TRIGGER `trg_ledger_transaction_no_delete`
    BEFORE DELETE ON `ledger_transaction`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'ledger_transaction is immutable';
END$$

CREATE TRIGGER `trg_wallet_entry_no_update`
    BEFORE UPDATE ON `wallet_entry`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'wallet_entry is immutable';
END$$

CREATE TRIGGER `trg_wallet_entry_no_delete`
    BEFORE DELETE ON `wallet_entry`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'wallet_entry is immutable';
END$$
DELIMITER ;
