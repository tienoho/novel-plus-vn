-- Quy trình vận hành đơn thuê bao đã thu tiền nhưng chưa thể kích hoạt.
-- Không tự hoàn tiền, không sửa ví và không thay đổi snapshot giao dịch.
SET NAMES utf8mb4;

SET @drop_old_rsp_status_check = IF(
    (SELECT COUNT(*) FROM information_schema.check_constraints
     WHERE constraint_schema = DATABASE()
       AND constraint_name = 'chk_rsp_status'
       AND check_clause NOT LIKE '%REFUND_PENDING%') > 0,
    'ALTER TABLE `reading_subscription_purchase` DROP CHECK `chk_rsp_status`',
    'SELECT 1'
);
PREPARE drop_old_rsp_status_check_statement FROM @drop_old_rsp_status_check;
EXECUTE drop_old_rsp_status_check_statement;
DEALLOCATE PREPARE drop_old_rsp_status_check_statement;

SET @add_rsp_status_check = IF(
    (SELECT COUNT(*) FROM information_schema.check_constraints
     WHERE constraint_schema = DATABASE()
       AND constraint_name = 'chk_rsp_status') = 0,
    'ALTER TABLE `reading_subscription_purchase` ADD CONSTRAINT `chk_rsp_status` CHECK (`status` IN (''PENDING'', ''ACTIVATED'', ''PAID_REVIEW'', ''REFUND_PENDING'', ''FAILED''))',
    'SELECT 1'
);
PREPARE add_rsp_status_check_statement FROM @add_rsp_status_check;
EXECUTE add_rsp_status_check_statement;
DEALLOCATE PREPARE add_rsp_status_check_statement;

CREATE TABLE IF NOT EXISTS `reading_subscription_purchase_review_audit`
(
    `id`                      BIGINT       NOT NULL AUTO_INCREMENT,
    `purchase_id`             BIGINT       NOT NULL,
    `out_trade_no`            BIGINT       NOT NULL,
    `event_type`              VARCHAR(32)  NOT NULL,
    `from_status`             VARCHAR(16)  NOT NULL,
    `to_status`               VARCHAR(16)  NOT NULL,
    `operator_id`             BIGINT       NOT NULL,
    `reason`                  VARCHAR(500) NOT NULL,
    `purchase_version_before` BIGINT       NOT NULL,
    `purchase_version_after`  BIGINT       NOT NULL,
    `create_time`             DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_rsp_review_purchase` (`purchase_id`, `create_time`, `id`),
    KEY `idx_rsp_review_operator` (`operator_id`, `create_time`, `id`),
    CONSTRAINT `chk_rsp_review_event` CHECK (`event_type` IN
        ('RETRY_ACTIVATED', 'RETRY_BLOCKED', 'REFUND_REQUESTED')),
    CONSTRAINT `chk_rsp_review_from` CHECK (`from_status` = 'PAID_REVIEW'),
    CONSTRAINT `chk_rsp_review_to` CHECK (`to_status` IN
        ('PAID_REVIEW', 'ACTIVATED', 'REFUND_PENDING')),
    CONSTRAINT `chk_rsp_review_reason` CHECK (CHAR_LENGTH(`reason`) BETWEEN 8 AND 500),
    CONSTRAINT `chk_rsp_review_version` CHECK
        (`purchase_version_after` >= `purchase_version_before`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
  COMMENT='Audit bất biến cho xử lý đơn thuê bao PAID_REVIEW';

DROP TRIGGER IF EXISTS `trg_rsp_review_audit_no_update`;
DROP TRIGGER IF EXISTS `trg_rsp_review_audit_no_delete`;
DELIMITER $$
CREATE TRIGGER `trg_rsp_review_audit_no_update`
BEFORE UPDATE ON `reading_subscription_purchase_review_audit`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Nhật ký xử lý đơn thuê bao là bất biến';
END$$
CREATE TRIGGER `trg_rsp_review_audit_no_delete`
BEFORE DELETE ON `reading_subscription_purchase_review_audit`
FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Nhật ký xử lý đơn thuê bao là bất biến';
END$$
DELIMITER ;

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Xử lý đơn thuê bao đã thanh toán', NULL,
       'novel:readingSubscription:review', 2, NULL, 3, NOW()
FROM (SELECT menu_id FROM `sys_menu`
      WHERE perms = 'novel:readingSubscription:view' LIMIT 1) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE perms = 'novel:readingSubscription:review'
);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT role.role_id, menu.menu_id
FROM `sys_role` role
JOIN `sys_menu` menu ON menu.perms = 'novel:readingSubscription:review'
WHERE role.role_sign = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` relation
      WHERE relation.role_id = role.role_id AND relation.menu_id = menu.menu_id
  );
