-- Migration cho R1 (Refund, Chargeback, Reconciliation) và R2 (VietQR/NAPAS Payment Adapter)
SET NAMES utf8mb4;

-- 1. Bảng quản lý Hoàn tiền và Chargeback
CREATE TABLE IF NOT EXISTS `order_refund` (
    `id`                             bigint(20)   NOT NULL AUTO_INCREMENT,
    `refund_no`                      varchar(64)  NOT NULL COMMENT 'Mã yêu cầu hoàn tiền',
    `out_trade_no`                   bigint(20)   NOT NULL COMMENT 'Mã đơn thanh toán gốc',
    `user_id`                        bigint(20)   NOT NULL COMMENT 'ID độc giả',
    `refund_amount_vnd`              int          NOT NULL COMMENT 'Số tiền VND hoàn',
    `refund_xu`                      bigint(20)   NOT NULL COMMENT 'Số Xu hoàn/trừ ví',
    `type`                           varchar(20)  NOT NULL COMMENT 'REFUND hoặc CHARGEBACK',
    `status`                         varchar(20)  NOT NULL DEFAULT 'REQUESTED' COMMENT 'REQUESTED, APPROVED, REVERSED, REJECTED, FAILED',
    `reason`                         varchar(500) DEFAULT NULL,
    `original_ledger_transaction_id` bigint(20)  DEFAULT NULL,
    `hold_ledger_transaction_id`     bigint(20)  DEFAULT NULL,
    `reversal_ledger_transaction_id` bigint(20)  DEFAULT NULL,
    `provider_reference`             varchar(128) DEFAULT NULL,
    `idempotency_key`                varchar(128) NOT NULL,
    `processed_by`                   bigint(20)  DEFAULT NULL,
    `processed_at`                   datetime(3)  DEFAULT NULL,
    `create_time`                    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`                    datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    `version`                        bigint(20)   NOT NULL DEFAULT 0,
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_refund_no` (`refund_no`),
    UNIQUE KEY `uk_refund_idempotency` (`idempotency_key`),
    UNIQUE KEY `uk_refund_order` (`out_trade_no`),
    UNIQUE KEY `uk_refund_reversal_ledger` (`reversal_ledger_transaction_id`),
    KEY `idx_refund_user` (`user_id`),
    CONSTRAINT `chk_refund_amounts` CHECK (`refund_amount_vnd` > 0 AND `refund_xu` > 0),
    CONSTRAINT `chk_refund_type` CHECK (`type` IN ('REFUND', 'CHARGEBACK')),
    CONSTRAINT `chk_refund_status` CHECK (`status` IN ('REQUESTED', 'APPROVED', 'REVERSED', 'REJECTED', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Yêu cầu hoàn tiền và chargeback';

-- CREATE TABLE IF NOT EXISTS không nâng cấp bảng đã tồn tại. Thủ tục dưới đây bổ sung
-- state machine, optimistic concurrency và chính sách nợ chargeback theo cách chạy lặp an toàn.
DROP PROCEDURE IF EXISTS `migrate_refund_state_machine`;
DELIMITER $$
CREATE PROCEDURE `migrate_refund_state_machine`()
BEGIN
    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_refund'
                     AND COLUMN_NAME = 'hold_ledger_transaction_id') THEN
        ALTER TABLE `order_refund`
            ADD COLUMN `hold_ledger_transaction_id` bigint(20) DEFAULT NULL
                AFTER `original_ledger_transaction_id`;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_refund'
                     AND COLUMN_NAME = 'provider_reference') THEN
        ALTER TABLE `order_refund`
            ADD COLUMN `provider_reference` varchar(128) DEFAULT NULL
                AFTER `reversal_ledger_transaction_id`;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.COLUMNS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_refund'
                     AND COLUMN_NAME = 'version') THEN
        ALTER TABLE `order_refund`
            ADD COLUMN `version` bigint(20) NOT NULL DEFAULT 0 AFTER `update_time`;
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_refund'
                     AND INDEX_NAME = 'uk_refund_order') THEN
        ALTER TABLE `order_refund` ADD UNIQUE KEY `uk_refund_order` (`out_trade_no`);
    END IF;

    IF NOT EXISTS (SELECT 1 FROM information_schema.STATISTICS
                   WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'order_refund'
                     AND INDEX_NAME = 'uk_refund_reversal_ledger') THEN
        ALTER TABLE `order_refund`
            ADD UNIQUE KEY `uk_refund_reversal_ledger` (`reversal_ledger_transaction_id`);
    END IF;

    IF EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'order_refund'
                 AND CONSTRAINT_NAME = 'chk_refund_amounts' AND CONSTRAINT_TYPE = 'CHECK') THEN
        ALTER TABLE `order_refund` DROP CHECK `chk_refund_amounts`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'order_refund'
                 AND CONSTRAINT_NAME = 'chk_refund_type' AND CONSTRAINT_TYPE = 'CHECK') THEN
        ALTER TABLE `order_refund` DROP CHECK `chk_refund_type`;
    END IF;
    IF EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'order_refund'
                 AND CONSTRAINT_NAME = 'chk_refund_status' AND CONSTRAINT_TYPE = 'CHECK') THEN
        ALTER TABLE `order_refund` DROP CHECK `chk_refund_status`;
    END IF;

    ALTER TABLE `order_refund`
        ADD CONSTRAINT `chk_refund_amounts` CHECK (`refund_amount_vnd` > 0 AND `refund_xu` > 0),
        ADD CONSTRAINT `chk_refund_type` CHECK (`type` IN ('REFUND', 'CHARGEBACK')),
        ADD CONSTRAINT `chk_refund_status` CHECK
            (`status` IN ('REQUESTED', 'APPROVED', 'REVERSED', 'REJECTED', 'FAILED'));

    IF EXISTS (SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
               WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'wallet_account'
                 AND CONSTRAINT_NAME = 'chk_wallet_available_balance' AND CONSTRAINT_TYPE = 'CHECK') THEN
        ALTER TABLE `wallet_account` DROP CHECK `chk_wallet_available_balance`;
    END IF;

    ALTER TABLE `wallet_account`
        ADD CONSTRAINT `chk_wallet_available_balance` CHECK
            (`owner_type` = 'SYSTEM' OR `available_balance` >= 0 OR
             (`owner_type` = 'USER' AND `account_type` = 'READER_XU' AND `status` = 'DEBT'));
END$$
DELIMITER ;

CALL `migrate_refund_state_machine`();
DROP PROCEDURE `migrate_refund_state_machine`;

CREATE TABLE IF NOT EXISTS `order_refund_audit` (
    `id`          bigint(20)   NOT NULL AUTO_INCREMENT,
    `refund_id`   bigint(20)   NOT NULL,
    `refund_no`   varchar(64)  NOT NULL,
    `event_type`  varchar(32)  NOT NULL,
    `from_status` varchar(20)  DEFAULT NULL,
    `to_status`   varchar(20)  NOT NULL,
    `actor_type`  varchar(16)  NOT NULL,
    `actor_id`    bigint(20)   DEFAULT NULL,
    `reason`      varchar(500) DEFAULT NULL,
    `create_time` datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_refund_audit_request_time` (`refund_id`, `create_time`),
    KEY `idx_refund_audit_no_time` (`refund_no`, `create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Lịch sử trạng thái refund/chargeback bất biến';

DROP TRIGGER IF EXISTS `trg_order_refund_audit_no_update`;
DROP TRIGGER IF EXISTS `trg_order_refund_audit_no_delete`;
DELIMITER $$
CREATE TRIGGER `trg_order_refund_audit_no_update`
    BEFORE UPDATE ON `order_refund_audit`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'order_refund_audit is immutable';
END$$

CREATE TRIGGER `trg_order_refund_audit_no_delete`
    BEFORE DELETE ON `order_refund_audit`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'order_refund_audit is immutable';
END$$
DELIMITER ;

-- 2. Bảng quản lý Lô đối soát ngân hàng
CREATE TABLE IF NOT EXISTS `bank_reconciliation_batch` (
    `id`                      bigint(20)  NOT NULL AUTO_INCREMENT,
    `batch_no`                varchar(64) NOT NULL COMMENT 'Mã lô đối soát',
    `pay_channel`             tinyint     NOT NULL COMMENT 'Mã kênh thanh toán',
    `reconcile_date`          date        NOT NULL COMMENT 'Ngày đối soát',
    `total_transactions`      int         NOT NULL DEFAULT 0,
    `matched_transactions`    int         NOT NULL DEFAULT 0,
    `mismatched_transactions` int         NOT NULL DEFAULT 0,
    `total_amount_vnd`        bigint(20)  NOT NULL DEFAULT 0,
    `status`                  varchar(20) NOT NULL DEFAULT 'COMPLETED',
    `create_time`             datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reconcile_batch_no` (`batch_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Lô đối soát ngân hàng';

-- 3. Bảng chi tiết giao dịch đối soát
CREATE TABLE IF NOT EXISTS `bank_reconciliation_item` (
    `id`                 bigint(20)   NOT NULL AUTO_INCREMENT,
    `batch_id`           bigint(20)   NOT NULL COMMENT 'ID lô đối soát',
    `out_trade_no`       bigint(20)   DEFAULT NULL COMMENT 'Mã đơn hệ thống',
    `bank_trade_no`      varchar(128) DEFAULT NULL COMMENT 'Mã giao dịch phía ngân hàng',
    `amount_vnd`         int          NOT NULL COMMENT 'Số tiền trên sao kê',
    `match_status`       varchar(30)  NOT NULL COMMENT 'MATCHED, AMOUNT_MISMATCH, NOT_FOUND_IN_SYSTEM, NOT_FOUND_IN_BANK',
    `discrepancy_reason` varchar(255) DEFAULT NULL,
    `create_time`        datetime(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_reconcile_item_batch` (`batch_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Chi tiết đối soát giao dịch ngân hàng';

-- 4. Bảng cấu hình kênh thanh toán
CREATE TABLE IF NOT EXISTS `payment_channel_config` (
    `id`           bigint(20)  NOT NULL AUTO_INCREMENT,
    `channel_code` tinyint     NOT NULL COMMENT '4: VNPAY, 5: VIETQR, 6: NAPAS_247',
    `channel_name` varchar(50) NOT NULL,
    `is_enabled`   tinyint(1)  NOT NULL DEFAULT 1,
    `config_json`  text        DEFAULT NULL COMMENT 'Cấu hình chi tiết mã hóa/JSON',
    `create_time`  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`  datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_payment_channel_code` (`channel_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Cấu hình kênh thanh toán';
