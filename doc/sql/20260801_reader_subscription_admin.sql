-- Menu và quyền vận hành thuê bao Vé đọc. Không seed plan thương mại, không tự kích hoạt user.
-- Các INSERT dùng permission làm khóa tự nhiên nên chạy lại an toàn và không ghi đè menu tùy chỉnh.

SET NAMES utf8mb4;

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT seed.parent_id, 'Thuê bao Vé đọc', 'novel/readingSubscription',
       'novel:readingSubscription:view', 1, 'fa fa-ticket', 9, NOW()
FROM (
    SELECT COALESCE((
        SELECT parent_id FROM `sys_menu` WHERE perms = 'novel:author:author' LIMIT 1
    ), 0) AS parent_id
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE perms = 'novel:readingSubscription:view'
);

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Cấu hình gói thuê bao', NULL,
       'novel:readingSubscription:config', 2, NULL, 1, NOW()
FROM (SELECT menu_id FROM `sys_menu`
      WHERE perms = 'novel:readingSubscription:view' LIMIT 1) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE perms = 'novel:readingSubscription:config'
);

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Kích hoạt thuê bao thủ công', NULL,
       'novel:readingSubscription:activate', 2, NULL, 2, NOW()
FROM (SELECT menu_id FROM `sys_menu`
      WHERE perms = 'novel:readingSubscription:view' LIMIT 1) parent
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE perms = 'novel:readingSubscription:activate'
);

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT role.role_id, menu.menu_id
FROM `sys_role` role
JOIN `sys_menu` menu
  ON menu.perms IN ('novel:readingSubscription:view',
                    'novel:readingSubscription:config',
                    'novel:readingSubscription:activate')
WHERE role.role_sign = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` relation
      WHERE relation.role_id = role.role_id AND relation.menu_id = menu.menu_id
  );
