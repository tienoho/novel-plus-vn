-- SQL menu
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    VALUES ('1', 'Quản lý quyền dữ liệu', 'system/dataPerm', 'system:dataPerm:dataPerm', '1', 'fa', '6');

-- ID menu cha của nút
set @parentId = @@identity;

-- SQL nút thuộc menu
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Xem', null, 'system:dataPerm:detail', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Thêm', null, 'system:dataPerm:add', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Sửa', null, 'system:dataPerm:edit', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Xóa', null, 'system:dataPerm:remove', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Xóa hàng loạt', null, 'system:dataPerm:batchRemove', '2', null, '6';
