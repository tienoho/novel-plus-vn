-- Hàng đợi thu hồi ủy quyền VNPAY Recurring; provider được gọi ngoài transaction người dùng.
SET NAMES utf8mb4;

ALTER TABLE `reading_subscription_mandate`
    DROP CHECK `chk_rs_mandate_status`;

ALTER TABLE `reading_subscription_mandate`
    MODIFY COLUMN `status` VARCHAR(24) NOT NULL DEFAULT 'PENDING',
    ADD COLUMN `revoke_requested_at` DATETIME(3) NULL AFTER `revoked_at`,
    ADD COLUMN `revoke_next_attempt_at` DATETIME(3) NULL AFTER `revoke_requested_at`,
    ADD COLUMN `revoke_lease_until` DATETIME(3) NULL AFTER `revoke_next_attempt_at`,
    ADD COLUMN `revoke_attempt_count` INT NOT NULL DEFAULT 0 AFTER `revoke_lease_until`,
    ADD COLUMN `revoke_last_error` VARCHAR(128) NULL AFTER `revoke_attempt_count`,
    MODIFY COLUMN `open_user_id` BIGINT GENERATED ALWAYS AS
        (CASE WHEN `status` IN ('PENDING', 'ACTIVE', 'REVOKE_PENDING')
              THEN `user_id` ELSE NULL END) STORED,
    ADD KEY `idx_rs_mandate_revoke_queue`
        (`status`, `revoke_next_attempt_at`, `revoke_lease_until`, `id`),
    ADD CONSTRAINT `chk_rs_mandate_status` CHECK
        (`status` IN ('PENDING', 'ACTIVE', 'REVOKE_PENDING', 'REVOKED', 'FAILED')),
    ADD CONSTRAINT `chk_rs_mandate_revoke_attempt_count` CHECK
        (`revoke_attempt_count` >= 0);
