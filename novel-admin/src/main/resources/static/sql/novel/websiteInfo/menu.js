--SQL menu
INSERT
INTO`sys_menu`(`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
VALUES('247', 'Bảng thông tin website', 'novel/websiteInfo', 'novel:websiteInfo:websiteInfo', '1', 'fa', '6');

--ID menu cha của nút
set
@parentId
= @@identity;

--SQL nút thuộc menu
INSERT
INTO`sys_menu`(`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
SELECT
@parentId,
'Xem', null, 'novel:websiteInfo:detail', '2', null, '6';
INSERT
INTO`sys_menu`(`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
SELECT
@parentId,
'Thêm', null, 'novel:websiteInfo:add', '2', null, '6';
INSERT
INTO`sys_menu`(`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
SELECT
@parentId,
'Sửa', null, 'novel:websiteInfo:edit', '2', null, '6';
INSERT
INTO`sys_menu`(`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
SELECT
@parentId,
'Xóa', null, 'novel:websiteInfo:remove', '2', null, '6';
INSERT
INTO`sys_menu`(`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`)
SELECT
@parentId,
'Xóa hàng loạt', null, 'novel:websiteInfo:batchRemove', '2', null, '6';
