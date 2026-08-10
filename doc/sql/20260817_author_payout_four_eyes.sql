-- Cưỡng chế maker-checker cho payout tác giả ở cả ứng dụng và database.
-- Hai cột nullable giữ migration tương thích với lịch sử; yêu cầu legacy thiếu approved_by
-- sẽ fail-closed và phải được đối soát, không suy đoán actor từ reviewed_by đã có thể bị ghi đè.

DROP PROCEDURE IF EXISTS `migrate_author_payout_four_eyes`;
DELIMITER $$
CREATE PROCEDURE `migrate_author_payout_four_eyes`()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'author_withdrawal_request'
          AND COLUMN_NAME = 'approved_by'
    ) THEN
        ALTER TABLE `author_withdrawal_request`
            ADD COLUMN `approved_by` bigint(20) DEFAULT NULL
                COMMENT 'Quản trị viên duyệt số tiền và thuế' AFTER `reviewed_by`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME = 'author_withdrawal_request'
          AND COLUMN_NAME = 'executed_by'
    ) THEN
        ALTER TABLE `author_withdrawal_request`
            ADD COLUMN `executed_by` bigint(20) DEFAULT NULL
                COMMENT 'Quản trị viên thực hiện lệnh payout' AFTER `approved_by`;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.TABLE_CONSTRAINTS
        WHERE CONSTRAINT_SCHEMA = DATABASE()
          AND TABLE_NAME = 'author_withdrawal_request'
          AND CONSTRAINT_NAME = 'chk_author_withdrawal_four_eyes'
          AND CONSTRAINT_TYPE = 'CHECK'
    ) THEN
        ALTER TABLE `author_withdrawal_request`
            ADD CONSTRAINT `chk_author_withdrawal_four_eyes`
                CHECK (`executed_by` IS NULL OR
                       (`approved_by` IS NOT NULL AND `approved_by` <> `executed_by`));
    END IF;
END$$
DELIMITER ;

CALL `migrate_author_payout_four_eyes`();
DROP PROCEDURE `migrate_author_payout_four_eyes`;

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Duyệt payout tác giả', NULL, 'novel:authorFinance:payout:approve',
       2, NULL, 4, NOW()
FROM (
    SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:authorFinance:view' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE perms = 'novel:authorFinance:payout:approve'
);

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Thực hiện payout tác giả', NULL, 'novel:authorFinance:payout:execute',
       2, NULL, 5, NOW()
FROM (
    SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:authorFinance:view' LIMIT 1
) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE perms = 'novel:authorFinance:payout:execute'
);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT role.role_id, menu.menu_id
FROM `sys_role` role
JOIN `sys_menu` menu
  ON menu.perms IN ('novel:authorFinance:payout:approve', 'novel:authorFinance:payout:execute')
WHERE role.role_sign = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` relation
      WHERE relation.role_id = role.role_id AND relation.menu_id = menu.menu_id
  );
