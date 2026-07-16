-- SQL menu
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    VALUES ('1', 'Bảng nội dung tác phẩm', 'novel/bookContent', 'novel:bookContent:bookContent', '1', 'fa', '6');

-- ID menu cha của nút
set @parentId = @@identity;

-- SQL nút thuộc menu
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Xem', null, 'novel:bookContent:detail', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Thêm', null, 'novel:bookContent:add', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Sửa', null, 'novel:bookContent:edit', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Xóa', null, 'novel:bookContent:remove', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Xóa hàng loạt', null, 'novel:bookContent:batchRemove', '2', null, '6';
