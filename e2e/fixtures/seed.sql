-- Fixture chỉ dành cho Playwright. Tệp này được mount bởi compose.e2e.yaml sau khi Flyway hoàn tất;
-- không được copy vào image migration hoặc chạy trên database production.
SET NAMES utf8mb4;

-- Tài khoản checker chỉ tồn tại trong database Playwright. Phiên đăng nhập được tạo trực tiếp
-- trong Redis bởi AdminE2eSessionFixtureTest; CAPTCHA production không bị tắt hoặc đi đường vòng.
INSERT INTO `sys_user`
    (`user_id`, `username`, `name`, `password`, `must_change_password`, `dept_id`, `status`,
     `user_id_create`, `gmt_create`, `gmt_modified`)
SELECT 990102, 'e2e-checker', 'Người duyệt E2E', '!session-fixture-only!', 0, 14, 1,
       1, NOW(), NOW()
WHERE NOT EXISTS (SELECT 1 FROM `sys_user` WHERE `user_id` = 990102);

INSERT INTO `sys_user_role` (`user_id`, `role_id`)
SELECT 990102, 1
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_user_role` WHERE `user_id` = 990102 AND `role_id` = 1
);

-- E2E dùng chính provider DB production-like. Revision bootstrap production vẫn fail-closed;
-- fixture riêng này chỉ bật các luồng Đuốc/vote/season mà Playwright cần kiểm tra.
UPDATE `gamification_runtime_config`
SET `status` = 'ARCHIVED', `archived_at` = NOW(3), `version` = `version` + 1
WHERE `status` = 'ACTIVE' AND `revision_code` <> 'e2e-enabled';

INSERT INTO `gamification_runtime_config`
    (`revision_no`, `revision_code`, `source_revision_id`, `status`, `activation_class`, `high_risk`,
     `policy_version`, `zone_id`,
     `event_enabled`, `event_drain_batch_size`, `event_drain_delay_ms`, `event_max_attempt`,
     `ticket_enabled`, `ticket_lot_validity_days`, `ticket_expiry_cron`,
     `ticket_expiry_batch_size`, `ticket_max_grant_per_batch`,
     `vote_enabled`, `vote_allow_crawled_books`, `vote_ip_hash_key_id`,
     `vote_max_tickets_per_request`, `vote_max_votes_per_day`, `vote_max_tickets_per_day`,
     `vote_max_tickets_per_book_season`, `vote_max_lots_per_spend`,
     `quest_enabled`, `quest_heartbeat_interval_seconds`, `quest_heartbeat_max_minutes_day`,
     `realm_enabled`, `realm_change_cooldown_hours`,
     `season_enabled`, `season_close_cron`, `season_close_drain_seconds`,
     `season_resume_delay_ms`, `season_review_window_hours`,
     `reward_enabled`, `reward_claim_window_days`, `reward_release_cron`,
     `job_lease_seconds`, `job_batch_size`, `config_hash`,
     `created_by`, `submitted_by`, `approved_by`, `activated_by`, `change_reason`,
     `submitted_at`, `approved_at`, `effective_at`, `activated_at`)
SELECT
    9000001, 'e2e-enabled', 1, 'ACTIVE', 'NEXT_SEASON', 1, 'v1', 'Asia/Ho_Chi_Minh',
    0, 200, 15000, 10,
    1, 60, '0 20 3 * * ?', 500, 1000,
    1, 0, 'v1', 10, 20, 50, 100, 50,
    0, 60, 180, 0, 24,
    1, '0 5 0 1 * ?', 60, 30000, 72,
    0, 7, '0 40 3 * * ?', 300, 500,
    SHA2('khoi-thu-e2e-gamification-config-v1', 256),
    1, 1, 2, 2, 'Revision chỉ dùng cho Playwright E2E',
    NOW(3), NOW(3), NOW(3), NOW(3)
WHERE NOT EXISTS (
    SELECT 1 FROM `gamification_runtime_config` WHERE `revision_code` = 'e2e-enabled'
);

UPDATE `gamification_runtime_config`
SET `status` = 'ACTIVE', `archived_at` = NULL, `version` = `version` + 1
WHERE `revision_code` = 'e2e-enabled' AND `status` <> 'ACTIVE';

INSERT INTO `gamification_runtime_config_audit`
    (`config_id`, `event_type`, `from_status`, `to_status`, `operator_id`, `reason`, `after_hash`)
SELECT `id`, 'E2E_ACTIVATED', NULL, 'ACTIVE', 2,
       'Revision chỉ dùng cho Playwright E2E', `config_hash`
FROM `gamification_runtime_config` config
WHERE config.`revision_code` = 'e2e-enabled'
  AND NOT EXISTS (
      SELECT 1 FROM `gamification_runtime_config_audit` audit
      WHERE audit.`config_id` = config.`id` AND audit.`event_type` = 'E2E_ACTIVATED'
  );

SET @book_id := 990000000000000001;
SET @author_id := 990000000000000002;
SET @vote_author_id := 990000000000000003;
SET @vote_book_id := 990000000000000004;
SET @free_chapter_id := 990000000000000010;
SET @paid_chapter_id := 990000000000000011;
SET @ticket_chapter_id := 990000000000000012;
SET @green_user_id := 991000000000000001;
SET @orange_user_id := 992000000000000001;
SET @dark_user_id := 993000000000000001;
SET @blue_user_id := 994000000000000001;
SET @vote_author_user_id := 995000000000000001;
SET @reader_password := '$2y$12$MUL7pjxeOMWS/P1fs3snseqhrvUOw8jrs82KtnY893SjbgaLM18Am';

INSERT INTO `user`
    (`id`, `username`, `password`, `nick_name`, `user_photo`, `user_sex`, `account_balance`,
     `status`, `create_time`, `update_time`, `date_of_birth`, `is_age_verified`)
VALUES
    (@green_user_id, '0901000001', @reader_password, 'Độc giả E2E Green', NULL, 0, 500,
     0, NOW() - INTERVAL 90 DAY, NOW(), '1995-01-01', 1),
    (@orange_user_id, '0901000002', @reader_password, 'Độc giả E2E Orange', NULL, 0, 500,
     0, NOW() - INTERVAL 90 DAY, NOW(), '1995-01-01', 1),
    (@dark_user_id, '0901000003', @reader_password, 'Độc giả E2E Dark', NULL, 1, 500,
     0, NOW() - INTERVAL 90 DAY, NOW(), '1995-01-01', 1),
    (@blue_user_id, '0901000004', @reader_password, 'Độc giả E2E Blue', NULL, 1, 500,
     0, NOW() - INTERVAL 90 DAY, NOW(), '1995-01-01', 1),
    (@vote_author_user_id, '0901000005', @reader_password, 'Tác giả E2E độc lập', NULL, 0, 0,
     0, NOW() - INTERVAL 90 DAY, NOW(), '1995-01-01', 1)
ON DUPLICATE KEY UPDATE
    `password` = VALUES(`password`),
    `nick_name` = VALUES(`nick_name`),
    `account_balance` = VALUES(`account_balance`),
    `status` = 0,
    `update_time` = NOW(),
    `date_of_birth` = VALUES(`date_of_birth`),
    `is_age_verified` = 1;

INSERT INTO `author`
    (`id`, `user_id`, `invite_code`, `pen_name`, `tel_phone`, `chat_account`, `email`,
     `work_direction`, `status`, `create_time`)
VALUES
    (@author_id, @green_user_id, 'E2E-AUTHOR-2026', 'Tác giả Sao Việt', '0901000001',
     'e2e-author', 'author-e2e@example.invalid', 0, 0, NOW() - INTERVAL 90 DAY),
    (@vote_author_id, @vote_author_user_id, 'E2E-VOTE-AUTHOR-2026', 'Tác giả Bình Chọn', '0901000005',
     'e2e-vote-author', 'vote-author-e2e@example.invalid', 0, 0, NOW() - INTERVAL 90 DAY)
ON DUPLICATE KEY UPDATE
    `user_id` = VALUES(`user_id`),
    `pen_name` = VALUES(`pen_name`),
    `status` = 0;

INSERT INTO `book`
    (`id`, `work_direction`, `cat_id`, `cat_name`, `pic_url`, `book_name`, `author_id`,
     `author_name`, `book_desc`, `score`, `book_status`, `visit_count`, `word_count`,
     `comment_count`, `last_index_id`, `last_index_name`, `last_index_update_time`, `is_vip`,
     `status`, `update_time`, `create_time`, `crawl_source_id`, `crawl_book_id`, `crawl_last_time`,
     `crawl_is_stop`, `age_rating`, `audit_status`, `audit_reason`)
VALUES
    (@book_id, 0, 1, 'Kỳ ảo Việt Nam', '/images/smlcover.png', 'Hành Trình Sao Việt',
     @author_id, 'Tác giả Sao Việt', 'Tác phẩm fixture ổn định cho kiểm thử trình duyệt Khởi Thư.',
     9.5, 0, 1200, 3600, 0, @ticket_chapter_id, 'Chương 3: Vé đọc', NOW(), 1,
     1, NOW(), NOW() - INTERVAL 60 DAY, NULL, NULL, NULL, 0, 0, 1, NULL)
ON DUPLICATE KEY UPDATE
    `book_name` = VALUES(`book_name`),
    `author_id` = VALUES(`author_id`),
    `author_name` = VALUES(`author_name`),
    `book_desc` = VALUES(`book_desc`),
    `last_index_id` = VALUES(`last_index_id`),
    `last_index_name` = VALUES(`last_index_name`),
    `last_index_update_time` = NOW(),
    `status` = 1,
    `audit_status` = 1,
    `update_time` = NOW();

INSERT INTO `book`
    (`id`, `work_direction`, `cat_id`, `cat_name`, `pic_url`, `book_name`, `author_id`,
     `author_name`, `book_desc`, `score`, `book_status`, `visit_count`, `word_count`,
     `comment_count`, `last_index_id`, `last_index_name`, `last_index_update_time`, `is_vip`,
     `status`, `update_time`, `create_time`, `crawl_source_id`, `crawl_book_id`, `crawl_last_time`,
     `crawl_is_stop`, `age_rating`, `audit_status`, `audit_reason`)
VALUES
    (@vote_book_id, 0, 1, 'Kỳ ảo Việt Nam', '/images/smlcover.png', 'Ngọn Đuốc Phương Nam',
     @vote_author_id, 'Tác giả Bình Chọn', 'Tác phẩm độc lập dùng để kiểm thử bình chọn không tự bỏ phiếu.',
     9.0, 0, 100, 1200, 0, NULL, NULL, NOW(), 0,
     1, NOW(), NOW() - INTERVAL 60 DAY, NULL, NULL, NULL, 0, 0, 1, NULL)
ON DUPLICATE KEY UPDATE
    `author_id` = VALUES(`author_id`),
    `author_name` = VALUES(`author_name`),
    `status` = 1,
    `audit_status` = 1,
    `update_time` = NOW();

INSERT INTO `book_index`
    (`id`, `book_id`, `index_num`, `index_name`, `word_count`, `is_vip`, `book_price`,
     `storage_type`, `create_time`, `update_time`, `audit_status`, `content_hash`, `sim_hash`)
VALUES
    (@free_chapter_id, @book_id, 1, 'Chương 1: Khởi hành', 1200, 0, 0, 'db', NOW(), NOW(), 1, NULL, NULL),
    (@paid_chapter_id, @book_id, 2, 'Chương 2: Cánh cổng VIP', 1200, 1, 20, 'db', NOW(), NOW(), 1, NULL, NULL),
    (@ticket_chapter_id, @book_id, 3, 'Chương 3: Vé đọc', 1200, 1, 25, 'db', NOW(), NOW(), 1, NULL, NULL)
ON DUPLICATE KEY UPDATE
    `index_name` = VALUES(`index_name`),
    `word_count` = VALUES(`word_count`),
    `is_vip` = VALUES(`is_vip`),
    `book_price` = VALUES(`book_price`),
    `storage_type` = 'db',
    `audit_status` = 1,
    `update_time` = NOW();

INSERT INTO `book_content0` (`id`, `index_id`, `content`)
VALUES (990000000000000110, @free_chapter_id,
        '<p>Nội dung chương miễn phí dành cho kiểm thử E2E.</p>')
ON DUPLICATE KEY UPDATE `content` = VALUES(`content`);
INSERT INTO `book_content1` (`id`, `index_id`, `content`)
VALUES (990000000000000111, @paid_chapter_id,
        '<p>Nội dung chương VIP đã được mở khóa an toàn.</p>')
ON DUPLICATE KEY UPDATE `content` = VALUES(`content`);
INSERT INTO `book_content2` (`id`, `index_id`, `content`)
VALUES (990000000000000112, @ticket_chapter_id,
        '<p>Nội dung chương được mở bằng Vé đọc.</p>')
ON DUPLICATE KEY UPDATE `content` = VALUES(`content`);

INSERT INTO `user_bookshelf`
    (`id`, `user_id`, `book_id`, `pre_content_id`, `create_time`, `update_time`)
VALUES
    (991000000000000010, @green_user_id, @book_id, @free_chapter_id, NOW(), NOW()),
    (992000000000000010, @orange_user_id, @book_id, @free_chapter_id, NOW(), NOW()),
    (993000000000000010, @dark_user_id, @book_id, @free_chapter_id, NOW(), NOW()),
    (994000000000000010, @blue_user_id, @book_id, @free_chapter_id, NOW(), NOW())
ON DUPLICATE KEY UPDATE `pre_content_id` = VALUES(`pre_content_id`), `update_time` = NOW();

INSERT INTO `user_read_history`
    (`id`, `user_id`, `book_id`, `pre_content_id`, `create_time`, `update_time`)
VALUES
    (991000000000000020, @green_user_id, @book_id, @free_chapter_id, NOW(), NOW()),
    (992000000000000020, @orange_user_id, @book_id, @free_chapter_id, NOW(), NOW()),
    (993000000000000020, @dark_user_id, @book_id, @free_chapter_id, NOW(), NOW()),
    (994000000000000020, @blue_user_id, @book_id, @free_chapter_id, NOW(), NOW())
ON DUPLICATE KEY UPDATE `pre_content_id` = VALUES(`pre_content_id`), `update_time` = NOW();

INSERT INTO `wallet_account`
    (`owner_type`, `owner_id`, `account_type`, `currency`, `available_balance`, `pending_balance`, `version`, `status`)
VALUES
    ('USER', @green_user_id, 'READER_XU', 'XU', 500, 0, 0, 'ACTIVE'),
    ('USER', @orange_user_id, 'READER_XU', 'XU', 500, 0, 0, 'ACTIVE'),
    ('USER', @dark_user_id, 'READER_XU', 'XU', 500, 0, 0, 'ACTIVE'),
    ('USER', @blue_user_id, 'READER_XU', 'XU', 500, 0, 0, 'ACTIVE'),
    ('AUTHOR', @author_id, 'AUTHOR_REVENUE_XU', 'XU', 0, 0, 0, 'ACTIVE')
ON DUPLICATE KEY UPDATE
    `available_balance` = VALUES(`available_balance`),
    `pending_balance` = 0,
    `version` = 0,
    `status` = 'ACTIVE';

INSERT INTO `reading_subscription_plan`
    (`id`, `plan_code`, `plan_name`, `plan_version`, `price_vnd`, `price_xu`,
     `tickets_per_period`, `period_months`, `ticket_validity_days`, `status`, `version`)
VALUES
    (990000000000000200, 'E2E_MONTHLY', 'Gói E2E Tháng', 1, 49000, 490,
     10, 1, 45, 'ACTIVE', 0)
ON DUPLICATE KEY UPDATE
    `plan_name` = VALUES(`plan_name`),
    `plan_version` = 1,
    `price_vnd` = 49000,
    `price_xu` = 490,
    `tickets_per_period` = 10,
    `status` = 'ACTIVE';

INSERT INTO `reading_ticket_account`
    (`id`, `user_id`, `available_balance`, `lifetime_granted`, `lifetime_spent`,
     `lifetime_expired`, `status`, `version`)
VALUES
    (991000000000000030, @green_user_id, 2, 2, 0, 0, 'ACTIVE', 0),
    (992000000000000030, @orange_user_id, 2, 2, 0, 0, 'ACTIVE', 0),
    (993000000000000030, @dark_user_id, 2, 2, 0, 0, 'ACTIVE', 0),
    (994000000000000030, @blue_user_id, 2, 2, 0, 0, 'ACTIVE', 0)
ON DUPLICATE KEY UPDATE
    `available_balance` = 2,
    `lifetime_granted` = 2,
    `lifetime_spent` = 0,
    `lifetime_expired` = 0,
    `status` = 'ACTIVE',
    `version` = 0;

INSERT INTO `reading_ticket_ledger`
    (`id`, `entry_no`, `user_id`, `entry_type`, `amount`, `balance_after`, `business_type`,
     `business_id`, `idempotency_key`, `request_hash`, `operator_type`, `policy_version`)
VALUES
    (991000000000000040, 'RT-E2E-GREEN', @green_user_id, 'GRANT', 2, 2, 'ADMIN_GRANT',
     'E2E-GREEN', 'E2E:RT:GREEN', REPEAT('1', 64), 'SYSTEM', 'v1'),
    (992000000000000040, 'RT-E2E-ORANGE', @orange_user_id, 'GRANT', 2, 2, 'ADMIN_GRANT',
     'E2E-ORANGE', 'E2E:RT:ORANGE', REPEAT('2', 64), 'SYSTEM', 'v1'),
    (993000000000000040, 'RT-E2E-DARK', @dark_user_id, 'GRANT', 2, 2, 'ADMIN_GRANT',
     'E2E-DARK', 'E2E:RT:DARK', REPEAT('3', 64), 'SYSTEM', 'v1'),
    (994000000000000040, 'RT-E2E-BLUE', @blue_user_id, 'GRANT', 2, 2, 'ADMIN_GRANT',
     'E2E-BLUE', 'E2E:RT:BLUE', REPEAT('4', 64), 'SYSTEM', 'v1')
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `reading_ticket_lot`
    (`id`, `user_id`, `source_type`, `source_ref`, `granted_amount`, `remaining_amount`,
     `grant_ledger_id`, `effective_at`, `expire_at`, `status`, `policy_version`, `version`)
VALUES
    (991000000000000050, @green_user_id, 'ADMIN_GRANT', 'E2E-GREEN', 2, 2,
     991000000000000040, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 30 DAY, 'ACTIVE', 'v1', 0),
    (992000000000000050, @orange_user_id, 'ADMIN_GRANT', 'E2E-ORANGE', 2, 2,
     992000000000000040, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 30 DAY, 'ACTIVE', 'v1', 0),
    (993000000000000050, @dark_user_id, 'ADMIN_GRANT', 'E2E-DARK', 2, 2,
     993000000000000040, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 30 DAY, 'ACTIVE', 'v1', 0),
    (994000000000000050, @blue_user_id, 'ADMIN_GRANT', 'E2E-BLUE', 2, 2,
     994000000000000040, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 30 DAY, 'ACTIVE', 'v1', 0)
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `monthly_ticket_season`
    (`id`, `period_code`, `season_type`, `zone_id`, `start_at`, `end_at`, `vote_cutoff_at`,
     `status`, `policy_version`, `version`)
VALUES
    (990000000000000300, DATE_FORMAT(NOW(), '%Y-%m'), 'REGULAR', 'Asia/Ho_Chi_Minh',
     NOW() - INTERVAL 2 DAY, NOW() + INTERVAL 20 DAY, NOW() + INTERVAL 20 DAY, 'OPEN', 'v1', 0),
    (990000000000000301, 'e2e-special', 'SPECIAL', 'Asia/Ho_Chi_Minh',
     NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 10 DAY, NOW() + INTERVAL 10 DAY, 'OPEN', 'v1', 0)
ON DUPLICATE KEY UPDATE
    `start_at` = VALUES(`start_at`),
    `end_at` = VALUES(`end_at`),
    `vote_cutoff_at` = VALUES(`vote_cutoff_at`),
    `status` = 'OPEN',
    `policy_version` = 'v1',
    `version` = 0;

INSERT INTO `monthly_rank_counter`
    (`season_id`, `book_id`, `total_tickets`, `vote_count`, `distinct_voter_count`, `version`)
VALUES
    (990000000000000300, @book_id, 0, 0, 0, 0),
    (990000000000000301, @book_id, 0, 0, 0, 0)
ON DUPLICATE KEY UPDATE `total_tickets` = VALUES(`total_tickets`), `version` = 0;

INSERT INTO `monthly_ticket_account`
    (`id`, `user_id`, `available_balance`, `lifetime_granted`, `lifetime_spent`,
     `lifetime_expired`, `lifetime_revoked`, `lifetime_adjusted`, `status`, `version`)
VALUES
    (991000000000000060, @green_user_id, 4, 4, 0, 0, 0, 0, 'ACTIVE', 0),
    (992000000000000060, @orange_user_id, 4, 4, 0, 0, 0, 0, 'ACTIVE', 0),
    (993000000000000060, @dark_user_id, 4, 4, 0, 0, 0, 0, 'ACTIVE', 0),
    (994000000000000060, @blue_user_id, 4, 4, 0, 0, 0, 0, 'ACTIVE', 0)
ON DUPLICATE KEY UPDATE
    `available_balance` = 4,
    `lifetime_granted` = 4,
    `lifetime_spent` = 0,
    `lifetime_expired` = 0,
    `lifetime_revoked` = 0,
    `lifetime_adjusted` = 0,
    `status` = 'ACTIVE',
    `version` = 0;

INSERT INTO `monthly_ticket_ledger`
    (`id`, `entry_no`, `user_id`, `entry_type`, `amount`, `balance_after`, `business_type`,
     `business_id`, `idempotency_key`, `request_hash`, `operator_type`, `policy_version`)
VALUES
    (991000000000000070, 'MT-E2E-GREEN', @green_user_id, 'GRANT', 4, 4, 'ADMIN_GRANT',
     'E2E-GREEN', 'E2E:MT:GREEN', REPEAT('5', 64), 'SYSTEM', 'v1'),
    (992000000000000070, 'MT-E2E-ORANGE', @orange_user_id, 'GRANT', 4, 4, 'ADMIN_GRANT',
     'E2E-ORANGE', 'E2E:MT:ORANGE', REPEAT('6', 64), 'SYSTEM', 'v1'),
    (993000000000000070, 'MT-E2E-DARK', @dark_user_id, 'GRANT', 4, 4, 'ADMIN_GRANT',
     'E2E-DARK', 'E2E:MT:DARK', REPEAT('7', 64), 'SYSTEM', 'v1'),
    (994000000000000070, 'MT-E2E-BLUE', @blue_user_id, 'GRANT', 4, 4, 'ADMIN_GRANT',
     'E2E-BLUE', 'E2E:MT:BLUE', REPEAT('8', 64), 'SYSTEM', 'v1')
ON DUPLICATE KEY UPDATE `id` = `id`;

INSERT INTO `monthly_ticket_lot`
    (`id`, `user_id`, `source_type`, `source_ref`, `granted_amount`, `remaining_amount`,
     `grant_ledger_id`, `effective_at`, `expire_at`, `status`, `policy_version`, `version`)
VALUES
    (991000000000000080, @green_user_id, 'ADMIN_GRANT', 'E2E-GREEN', 4, 4,
     991000000000000070, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 30 DAY, 'ACTIVE', 'v1', 0),
    (992000000000000080, @orange_user_id, 'ADMIN_GRANT', 'E2E-ORANGE', 4, 4,
     992000000000000070, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 30 DAY, 'ACTIVE', 'v1', 0),
    (993000000000000080, @dark_user_id, 'ADMIN_GRANT', 'E2E-DARK', 4, 4,
     993000000000000070, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 30 DAY, 'ACTIVE', 'v1', 0),
    (994000000000000080, @blue_user_id, 'ADMIN_GRANT', 'E2E-BLUE', 4, 4,
     994000000000000070, NOW() - INTERVAL 1 DAY, NOW() + INTERVAL 30 DAY, 'ACTIVE', 'v1', 0)
ON DUPLICATE KEY UPDATE `id` = `id`;
