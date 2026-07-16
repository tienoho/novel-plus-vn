-- SQL menu
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    VALUES ('1', 'Bảng cấu hình tác phẩm trang chủ', 'novel/bookSetting', 'novel:bookSetting:bookSetting', '1', 'fa', '6');

-- ID menu cha của nút
set @parentId = @@identity;

-- SQL nút thuộc menu
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Xem', null, 'novel:bookSetting:detail', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Thêm', null, 'novel:bookSetting:add', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Sửa', null, 'novel:bookSetting:edit', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Xóa', null, 'novel:bookSetting:remove', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Xóa hàng loạt', null, 'novel:bookSetting:batchRemove', '2', null, '6';
