-- Cho phép kỳ đặc biệt chạy song song kỳ tháng và dùng slug ổn định tối đa 32 ký tự.
-- Flyway chạy migration này đúng một lần; migration lịch sử 20260729 không bị chỉnh sửa.

ALTER TABLE `monthly_ticket_season`
    MODIFY COLUMN `period_code` VARCHAR(32) NOT NULL COMMENT 'yyyy-MM cho kỳ thường hoặc slug kỳ đặc biệt',
    DROP INDEX `uk_mt_season_start`,
    ADD KEY `idx_mt_season_start` (`start_at`);

ALTER TABLE `reward_fund_campaign`
    MODIFY COLUMN `period_code` VARCHAR(32) NOT NULL;
