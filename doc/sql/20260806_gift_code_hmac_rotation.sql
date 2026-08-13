-- Định danh khóa HMAC để xoay khóa mà không làm mất khả năng đổi mã quà cũ.
-- Không re-hash, không lưu plaintext và không thay đổi redemption/ledger hiện hữu.
SET NAMES utf8mb4;

SET @add_gift_hmac_key_id = IF(
    (SELECT COUNT(*) FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'gift_code'
       AND column_name = 'hmac_key_id') = 0,
    'ALTER TABLE `gift_code` ADD COLUMN `hmac_key_id` VARCHAR(32) CHARACTER SET ascii COLLATE ascii_bin NOT NULL DEFAULT ''legacy-v1'' AFTER `campaign_id`',
    'SELECT 1'
);
PREPARE add_gift_hmac_key_id_statement FROM @add_gift_hmac_key_id;
EXECUTE add_gift_hmac_key_id_statement;
DEALLOCATE PREPARE add_gift_hmac_key_id_statement;

SET @add_gift_hmac_key_hash_index = IF(
    (SELECT COUNT(*) FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'gift_code'
       AND index_name = 'uk_gift_code_key_hash') = 0,
    'ALTER TABLE `gift_code` ADD UNIQUE KEY `uk_gift_code_key_hash` (`hmac_key_id`, `code_hash`)',
    'SELECT 1'
);
PREPARE add_gift_hmac_key_hash_index_statement FROM @add_gift_hmac_key_hash_index;
EXECUTE add_gift_hmac_key_hash_index_statement;
DEALLOCATE PREPARE add_gift_hmac_key_hash_index_statement;

SET @add_gift_hmac_key_id_check = IF(
    (SELECT COUNT(*) FROM information_schema.check_constraints
     WHERE constraint_schema = DATABASE()
       AND constraint_name = 'chk_gift_code_hmac_key_id') = 0,
    'ALTER TABLE `gift_code` ADD CONSTRAINT `chk_gift_code_hmac_key_id` CHECK (`hmac_key_id` REGEXP ''^[A-Za-z0-9._-]{1,32}$'')',
    'SELECT 1'
);
PREPARE add_gift_hmac_key_id_check_statement FROM @add_gift_hmac_key_id_check;
EXECUTE add_gift_hmac_key_id_check_statement;
DEALLOCATE PREPARE add_gift_hmac_key_id_check_statement;

DROP TRIGGER IF EXISTS `trg_gift_code_hash_identity_no_update`;
DELIMITER $$
CREATE TRIGGER `trg_gift_code_hash_identity_no_update`
BEFORE UPDATE ON `gift_code`
FOR EACH ROW
BEGIN
    IF NOT (NEW.campaign_id <=> OLD.campaign_id)
       OR NOT (NEW.hmac_key_id <=> OLD.hmac_key_id)
       OR NOT (NEW.code_hash <=> OLD.code_hash)
       OR NOT (NEW.code_hint <=> OLD.code_hint)
       OR NOT (NEW.max_redemptions <=> OLD.max_redemptions) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Danh tính HMAC mã quà là bất biến';
    END IF;
END$$
DELIMITER ;
