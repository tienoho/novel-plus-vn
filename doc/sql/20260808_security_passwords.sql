-- Mở rộng cột cho BCrypt và vô hiệu hóa toàn bộ hash MD5 cũ.
-- Tài khoản admin được kích hoạt bằng ADMIN_BOOTSTRAP_PASSWORD ở lần chạy đầu.

SET @must_change_column_exists = (
    SELECT COUNT(*)
    FROM information_schema.columns
    WHERE table_schema = DATABASE()
      AND table_name = 'sys_user'
      AND column_name = 'must_change_password'
);
SET @must_change_column_sql = IF(
    @must_change_column_exists = 0,
    'ALTER TABLE `sys_user` ADD COLUMN `must_change_password` tinyint(1) NOT NULL DEFAULT 0 COMMENT ''Bắt buộc đổi mật khẩu'' AFTER `password`',
    'SELECT 1'
);
PREPARE must_change_column_statement FROM @must_change_column_sql;
EXECUTE must_change_column_statement;
DEALLOCATE PREPARE must_change_column_statement;

ALTER TABLE `sys_user`
    MODIFY COLUMN `password` varchar(100) DEFAULT NULL COMMENT 'Mật khẩu BCrypt';

UPDATE `sys_user`
SET `password` = '!RESET_REQUIRED!'
WHERE `password` IS NOT NULL
  AND `password` NOT LIKE '$2%';

UPDATE `user`
SET `password` = '!RESET_REQUIRED!'
WHERE `password` IS NOT NULL
  AND `password` NOT LIKE '$2%';
