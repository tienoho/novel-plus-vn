-- Snapshot idempotency cho khởi tạo mandate; dataKey chỉ tồn tại ở dạng mã hóa khi mandate còn PENDING.
SET NAMES utf8mb4;

ALTER TABLE `reading_subscription_mandate`
    ADD COLUMN `client_request_id` VARCHAR(64) NULL AFTER `merchant_reference`,
    ADD COLUMN `request_hash` CHAR(64) NULL AFTER `client_request_id`,
    ADD COLUMN `plan_code_snapshot` VARCHAR(32) NULL AFTER `request_hash`,
    ADD COLUMN `accepted_plan_version` BIGINT NULL AFTER `plan_code_snapshot`,
    ADD COLUMN `provider_data_key_ciphertext` VARCHAR(4096) NULL AFTER `provider_recurring_id`,
    ADD UNIQUE KEY `uk_rs_mandate_client_request` (`user_id`, `client_request_id`),
    ADD CONSTRAINT `chk_rs_mandate_request_snapshot` CHECK
        ((`client_request_id` IS NULL AND `request_hash` IS NULL
          AND `plan_code_snapshot` IS NULL AND `accepted_plan_version` IS NULL)
         OR
         (`client_request_id` IS NOT NULL AND CHAR_LENGTH(`client_request_id`) BETWEEN 8 AND 64
          AND `request_hash` REGEXP '^[0-9a-f]{64}$'
          AND `plan_code_snapshot` IS NOT NULL
          AND CHAR_LENGTH(`plan_code_snapshot`) BETWEEN 2 AND 32
          AND `accepted_plan_version` >= 1));

DROP TRIGGER IF EXISTS `trg_rs_mandate_request_snapshot_no_update`;
DELIMITER $$
CREATE TRIGGER `trg_rs_mandate_request_snapshot_no_update`
BEFORE UPDATE ON `reading_subscription_mandate`
FOR EACH ROW
BEGIN
    IF NOT (NEW.client_request_id <=> OLD.client_request_id)
       OR NOT (NEW.request_hash <=> OLD.request_hash)
       OR NOT (NEW.plan_code_snapshot <=> OLD.plan_code_snapshot)
       OR NOT (NEW.accepted_plan_version <=> OLD.accepted_plan_version) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Không được sửa snapshot idempotency của mandate';
    END IF;
END$$
DELIMITER ;
