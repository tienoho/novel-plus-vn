-- Quyền thu hồi mã quà chưa sử dụng. Không sửa hoặc xóa biên nhận đã phát sinh.

SET NAMES utf8mb4;

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Thu hồi mã quà chưa sử dụng', NULL,
       'novel:giftCode:revoke', 2, NULL, 3, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:giftCode:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:giftCode:revoke');

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT role.role_id, menu.menu_id
FROM `sys_role` role
JOIN `sys_menu` menu ON menu.perms = 'novel:giftCode:revoke'
WHERE role.role_sign = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` relation
      WHERE relation.role_id=role.role_id AND relation.menu_id=menu.menu_id
  );
