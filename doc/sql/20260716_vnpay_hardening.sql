-- Lưu số Xu đã cam kết cho từng đơn VNPAY và bảo đảm mã đơn không trùng.
-- Migration dùng information_schema để có thể chạy lại an toàn trên MySQL 8.

SET NAMES utf8mb4;

SET @add_account_amount = IF(
    (SELECT COUNT(*)
     FROM information_schema.columns
     WHERE table_schema = DATABASE()
       AND table_name = 'order_pay'
       AND column_name = 'account_amount') = 0,
    'ALTER TABLE `order_pay` ADD COLUMN `account_amount` int NULL COMMENT ''Số Xu cam kết cấp khi thanh toán thành công'' AFTER `total_amount`',
    'SELECT 1'
);
PREPARE add_account_amount_statement FROM @add_account_amount;
EXECUTE add_account_amount_statement;
DEALLOCATE PREPARE add_account_amount_statement;

-- Nếu dữ liệu cũ có mã đơn trùng, ALTER TABLE sẽ dừng thay vì âm thầm sửa hoặc mất lịch sử.
SET @add_out_trade_no_unique_index = IF(
    (SELECT COUNT(*)
     FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'order_pay'
       AND index_name = 'uk_order_pay_out_trade_no') = 0,
    'ALTER TABLE `order_pay` ADD UNIQUE INDEX `uk_order_pay_out_trade_no` (`out_trade_no`)',
    'SELECT 1'
);
PREPARE add_out_trade_no_unique_index_statement FROM @add_out_trade_no_unique_index;
EXECUTE add_out_trade_no_unique_index_statement;
DEALLOCATE PREPARE add_out_trade_no_unique_index_statement;

SET @add_reconciliation_index = IF(
    (SELECT COUNT(*)
     FROM information_schema.statistics
     WHERE table_schema = DATABASE()
       AND table_name = 'order_pay'
       AND index_name = 'idx_order_pay_vnpay_reconcile') = 0,
    'ALTER TABLE `order_pay` ADD INDEX `idx_order_pay_vnpay_reconcile` (`pay_channel`, `pay_status`, `create_time`, `update_time`)',
    'SELECT 1'
);
PREPARE add_reconciliation_index_statement FROM @add_reconciliation_index;
EXECUTE add_reconciliation_index_statement;
DEALLOCATE PREPARE add_reconciliation_index_statement;
