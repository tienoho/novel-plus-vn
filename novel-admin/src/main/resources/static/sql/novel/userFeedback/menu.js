-- SQL menu
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    VALUES ('1', '', 'novel/userFeedback', 'novel:userFeedback:userFeedback', '1', 'fa', '6');

-- ID menu cha của nút
set @parentId = @@identity;

-- SQL nút thuộc menu
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Xem', null, 'novel:userFeedback:detail', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Thêm', null, 'novel:userFeedback:add', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Sửa', null, 'novel:userFeedback:edit', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Xóa', null, 'novel:userFeedback:remove', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Xóa hàng loạt', null, 'novel:userFeedback:batchRemove', '2', null, '6';
