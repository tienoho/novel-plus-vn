-- SQL menu
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    VALUES ('1', 'Bảng bình luận tác phẩm', 'novel/bookComment', 'novel:bookComment:bookComment', '1', 'fa', '6');

-- ID menu cha của nút
set @parentId = @@identity;

-- SQL nút thuộc menu
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Xem', null, 'novel:bookComment:detail', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Thêm', null, 'novel:bookComment:add', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Sửa', null, 'novel:bookComment:edit', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Xóa', null, 'novel:bookComment:remove', '2', null, '6';
INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
    SELECT @parentId, 'Xóa hàng loạt', null, 'novel:bookComment:batchRemove', '2', null, '6';
