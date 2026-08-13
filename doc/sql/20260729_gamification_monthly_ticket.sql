-- Gamification: Ngọn Đuốc, nhiệm vụ, EXP, cảnh giới, kỳ xếp hạng và quỹ thưởng tác giả.
--
-- Một migration duy nhất cho cả ba giai đoạn P0, P1 và P2. Lý do gộp: mỗi migration phải được
-- khai báo ở hai chỗ trong compose.yaml (vòng lặp của service migrate và danh sách volumes), nên
-- mỗi lần tách file là một cơ hội quên mount và tạo lỗi thầm lặng.
--
-- Quy ước kế thừa 20260717_wallet_ledger.sql:
--   - Không dùng FOREIGN KEY. Toàn vẹn tham chiếu được bảo đảm bằng UNIQUE KEY, ràng buộc giao
--     dịch ở tầng service và job đối soát định kỳ, giống hệt sổ cái Xu.
--   - Bảng sổ cái là bất biến, chặn bằng trigger SIGNAL SQLSTATE '45000'.
--   - Bảng chiếu (projection) được phép UPDATE và luôn có cột version cho khoá lạc quan.
--
-- Ngọn Đuốc KHÔNG phải tài sản tài chính: không đổi ngược thành Xu, không chuyển nhượng, và
-- KHÔNG có trạng thái nợ. Đây là khác biệt cố ý so với ví Xu độc giả, nơi chargeback được phép
-- đẩy số dư xuống âm. Ràng buộc CHECK dưới đây là hàng rào cuối cho khác biệt đó.
--
-- Chính sách và lý do chọn từng giá trị mặc định: doc/gamification-policy-v1.md.

SET NAMES utf8mb4;

-- ---------------------------------------------------------------------------
-- A. Tài khoản hệ thống cho quỹ thưởng
-- ---------------------------------------------------------------------------
-- Thưởng xếp hạng đi qua ví clearing này trước khi vào ví doanh thu tác giả. Nếu ghi thẳng vào
-- AUTHOR_REVENUE_XU thì tác giả rút được ngay, và khi cần thu hồi, bút toán đảo sẽ thất bại vì
-- chk_wallet_available_balance cấm số dư âm với ví không thuộc hệ thống.
-- ensureWallets() ở tầng service vốn tự tạo ví hệ thống khi dùng lần đầu; seed tường minh ở đây
-- để truy vấn đối soát join được ngay sau khi deploy thay vì sau giao dịch thưởng đầu tiên.
INSERT IGNORE INTO `wallet_account`
    (`owner_type`, `owner_id`, `account_type`, `currency`, `available_balance`, `pending_balance`, `version`, `status`)
VALUES ('SYSTEM', 0, 'REWARD_CLEARING', 'XU', 0, 0, 0, 'ACTIVE');

-- ---------------------------------------------------------------------------
-- B. Sổ cái Ngọn Đuốc
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `monthly_ticket_account`
(
    `id`                BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`           BIGINT      NOT NULL,
    `available_balance` BIGINT      NOT NULL DEFAULT 0,
    `lifetime_granted`  BIGINT      NOT NULL DEFAULT 0,
    `lifetime_spent`    BIGINT      NOT NULL DEFAULT 0,
    `lifetime_expired`  BIGINT      NOT NULL DEFAULT 0,
    `lifetime_revoked`  BIGINT      NOT NULL DEFAULT 0,
    `lifetime_adjusted` BIGINT      NOT NULL DEFAULT 0 COMMENT 'Tổng điều chỉnh có dấu, cho phép âm',
    `status`            VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    `version`           BIGINT      NOT NULL DEFAULT 0,
    `create_time`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mt_account_user` (`user_id`),
    CONSTRAINT `chk_mt_account_balance` CHECK (`available_balance` >= 0),
    CONSTRAINT `chk_mt_account_lifetime` CHECK (`lifetime_granted` >= 0 AND `lifetime_spent` >= 0
        AND `lifetime_expired` >= 0 AND `lifetime_revoked` >= 0),
    CONSTRAINT `chk_mt_account_status` CHECK (`status` IN ('ACTIVE', 'FROZEN'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Số dư Ngọn Đuốc dạng projection; lot và sổ cái là nguồn kiểm toán';

CREATE TABLE IF NOT EXISTS `monthly_ticket_lot`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`          BIGINT       NOT NULL,
    `source_type`      VARCHAR(32)  NOT NULL,
    `source_ref`       VARCHAR(128) NOT NULL,
    `granted_amount`   BIGINT       NOT NULL,
    `remaining_amount` BIGINT       NOT NULL,
    `grant_ledger_id`  BIGINT       NOT NULL,
    `effective_at`     DATETIME(3)  NOT NULL,
    `expire_at`        DATETIME(3)  NOT NULL,
    `status`           VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE',
    `closed_at`        DATETIME(3)           DEFAULT NULL,
    `policy_version`   VARCHAR(32)  NOT NULL,
    `version`          BIGINT       NOT NULL DEFAULT 0,
    `create_time`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    -- Một bút toán cấp sinh đúng một lot. Chạy lại không tạo lot thứ hai kể cả khi tầng trên sai.
    UNIQUE KEY `uk_mt_lot_grant_ledger` (`grant_ledger_id`),
    -- Phục vụ trực tiếp thứ tự tiêu FIFO, tránh filesort khi đang giữ khoá FOR UPDATE.
    KEY `idx_mt_lot_fifo` (`user_id`, `status`, `expire_at`, `id`),
    KEY `idx_mt_lot_expiry_sweep` (`status`, `expire_at`),
    KEY `idx_mt_lot_source` (`source_type`, `source_ref`),
    CONSTRAINT `chk_mt_lot_amounts` CHECK (`granted_amount` > 0 AND `remaining_amount` >= 0
        AND `remaining_amount` <= `granted_amount`),
    CONSTRAINT `chk_mt_lot_window` CHECK (`expire_at` > `effective_at`),
    CONSTRAINT `chk_mt_lot_status` CHECK (`status` IN ('ACTIVE', 'EXHAUSTED', 'EXPIRED', 'REVOKED')),
    CONSTRAINT `chk_mt_lot_closed` CHECK (
        (`status` = 'ACTIVE' AND `remaining_amount` > 0 AND `closed_at` IS NULL)
            OR (`status` IN ('EXHAUSTED', 'EXPIRED', 'REVOKED')
                AND `remaining_amount` = 0 AND `closed_at` IS NOT NULL)),
    CONSTRAINT `chk_mt_lot_source_type` CHECK (`source_type` IN
        ('ADMIN_GRANT', 'PROMOTION', 'QUEST', 'CHECK_IN', 'LEVEL_UP', 'COMPENSATION'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Lô Ngọn Đuốc được cấp, tiêu theo FIFO ưu tiên lot sắp tắt trước';

CREATE TABLE IF NOT EXISTS `monthly_ticket_ledger`
(
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT,
    `entry_no`               VARCHAR(64)  NOT NULL,
    `user_id`                BIGINT       NOT NULL,
    `entry_type`             VARCHAR(24)  NOT NULL,
    `amount`                 BIGINT       NOT NULL COMMENT 'Dương khi cấp, âm khi tiêu, hết hạn hoặc thu hồi',
    `balance_after`          BIGINT       NOT NULL,
    `business_type`          VARCHAR(32)  NOT NULL,
    `business_id`            VARCHAR(128) NOT NULL,
    `idempotency_key`        VARCHAR(128) NOT NULL,
    `request_hash`           CHAR(64)     NOT NULL,
    `season_id`              BIGINT                DEFAULT NULL,
    `book_id`                BIGINT                DEFAULT NULL,
    `reversal_of_ledger_id`  BIGINT                DEFAULT NULL,
    `operator_type`          VARCHAR(16)  NOT NULL DEFAULT 'SYSTEM',
    `operator_id`            BIGINT                DEFAULT NULL,
    `reason`                 VARCHAR(255)          DEFAULT NULL,
    `policy_version`         VARCHAR(32)  NOT NULL,
    `create_time`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mt_ledger_entry_no` (`entry_no`),
    -- Trọng tài idempotency cuối cùng. Tầng service dựa vào DuplicateKeyException từ khoá này,
    -- giống hệt cách WalletLedgerServiceImpl.post() dựa vào uk_ledger_idempotency_key.
    UNIQUE KEY `uk_mt_ledger_idempotency` (`idempotency_key`),
    UNIQUE KEY `uk_mt_ledger_single_reversal` (`reversal_of_ledger_id`),
    KEY `idx_mt_ledger_user` (`user_id`, `id`),
    KEY `idx_mt_ledger_business` (`business_type`, `business_id`),
    KEY `idx_mt_ledger_season_book` (`season_id`, `book_id`),
    CONSTRAINT `chk_mt_ledger_amount` CHECK (`amount` <> 0),
    CONSTRAINT `chk_mt_ledger_balance_after` CHECK (`balance_after` >= 0),
    CONSTRAINT `chk_mt_ledger_type` CHECK (`entry_type` IN
        ('GRANT', 'SPEND', 'EXPIRE', 'REVOKE', 'ADJUST', 'REVERSAL')),
    CONSTRAINT `chk_mt_ledger_sign` CHECK (
        (`entry_type` = 'GRANT' AND `amount` > 0)
            OR (`entry_type` IN ('SPEND', 'EXPIRE', 'REVOKE') AND `amount` < 0)
            OR `entry_type` IN ('ADJUST', 'REVERSAL')),
    CONSTRAINT `chk_mt_ledger_operator_type` CHECK (`operator_type` IN ('SYSTEM', 'ADMIN', 'USER')),
    -- Thao tác của quản trị viên bắt buộc truy được người thực hiện và lý do.
    CONSTRAINT `chk_mt_ledger_operator` CHECK (`operator_type` <> 'ADMIN'
        OR (`operator_id` IS NOT NULL AND `reason` IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Bút toán Ngọn Đuốc bất biến; sửa sai bằng bút toán đảo';

CREATE TABLE IF NOT EXISTS `monthly_ticket_lot_allocation`
(
    `id`                  BIGINT      NOT NULL AUTO_INCREMENT,
    `ledger_id`           BIGINT      NOT NULL,
    `lot_id`              BIGINT      NOT NULL,
    `amount`              BIGINT      NOT NULL COMMENT 'Luôn dương; chiều suy từ entry_type của bút toán',
    `lot_remaining_after` BIGINT      NOT NULL,
    `create_time`         DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    -- Chống ghi kép khi vòng lặp FIFO bị chạy lại.
    UNIQUE KEY `uk_mt_alloc_ledger_lot` (`ledger_id`, `lot_id`),
    KEY `idx_mt_alloc_lot` (`lot_id`, `id`),
    CONSTRAINT `chk_mt_alloc_amount` CHECK (`amount` > 0),
    CONSTRAINT `chk_mt_alloc_remaining` CHECK (`lot_remaining_after` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Ánh xạ bút toán sang lot đã tiêu; tổng phân bổ phải bằng trị tuyệt đối của bút toán';

CREATE TABLE IF NOT EXISTS `monthly_ticket_vote`
(
    `id`                BIGINT       NOT NULL AUTO_INCREMENT,
    `season_id`         BIGINT       NOT NULL,
    `book_id`           BIGINT       NOT NULL,
    `author_id`         BIGINT                DEFAULT NULL COMMENT 'Ảnh chụp tại thời điểm bỏ phiếu; book.author_id là NULLABLE',
    `user_id`           BIGINT       NOT NULL,
    `ticket_count`      BIGINT       NOT NULL,
    `ledger_id`         BIGINT       NOT NULL,
    `idempotency_key`   VARCHAR(128) NOT NULL,
    `request_hash`      CHAR(64)     NOT NULL,
    `client_request_id` VARCHAR(64)  NOT NULL,
    `status`            VARCHAR(16)  NOT NULL DEFAULT 'VALID',
    `voided_ledger_id`  BIGINT                DEFAULT NULL,
    `void_reason`       VARCHAR(255)          DEFAULT NULL,
    `source_ip_hash`    CHAR(64)              DEFAULT NULL COMMENT 'SHA-256 kèm muối; không lưu IP dạng thô',
    `policy_version`    VARCHAR(32)  NOT NULL,
    `create_time`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`       DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mt_vote_idempotency` (`idempotency_key`),
    UNIQUE KEY `uk_mt_vote_user_client` (`user_id`, `client_request_id`),
    UNIQUE KEY `uk_mt_vote_ledger` (`ledger_id`),
    KEY `idx_mt_vote_season_book` (`season_id`, `book_id`, `status`, `id`),
    KEY `idx_mt_vote_user_season` (`user_id`, `season_id`, `create_time`),
    CONSTRAINT `chk_mt_vote_count` CHECK (`ticket_count` > 0),
    CONSTRAINT `chk_mt_vote_status` CHECK (`status` IN ('VALID', 'VOIDED')),
    CONSTRAINT `chk_mt_vote_void` CHECK (`status` = 'VALID'
        OR (`voided_ledger_id` IS NOT NULL AND `void_reason` IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Phiếu đã thắp cho tác phẩm; nguồn sự thật khi chụp snapshot xếp hạng';

-- ---------------------------------------------------------------------------
-- C. Giới hạn chống lạm dụng ở tầng database
-- ---------------------------------------------------------------------------
-- Hai bảng dưới đây là hàng rào cuối, không phải bộ nhớ đệm. RateLimitAspect hiện có tụt về bộ
-- đếm trong bộ nhớ theo từng instance khi Redis không khả dụng, nên nó không đủ cho một tài sản
-- có giá trị. Cập nhật bằng UPDATE có điều kiện trong cùng transaction với việc tiêu phiếu.

CREATE TABLE IF NOT EXISTS `monthly_ticket_daily_counter`
(
    `user_id`       BIGINT      NOT NULL,
    `local_date`    DATE        NOT NULL COMMENT 'Ngày theo múi giờ cấu hình, tính ở Java',
    `vote_count`    BIGINT      NOT NULL DEFAULT 0,
    `ticket_spent`  BIGINT      NOT NULL DEFAULT 0,
    `ticket_granted` BIGINT     NOT NULL DEFAULT 0,
    `version`       BIGINT      NOT NULL DEFAULT 0,
    `create_time`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`user_id`, `local_date`),
    CONSTRAINT `chk_mt_daily_counter` CHECK (`vote_count` >= 0 AND `ticket_spent` >= 0
        AND `ticket_granted` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Trần bỏ phiếu theo ngày, có hiệu lực kể cả khi Redis ngừng hoạt động';

CREATE TABLE IF NOT EXISTS `monthly_ticket_user_book_quota`
(
    `user_id`      BIGINT      NOT NULL,
    `season_id`    BIGINT      NOT NULL,
    `book_id`      BIGINT      NOT NULL,
    `ticket_spent` BIGINT      NOT NULL DEFAULT 0,
    `version`      BIGINT      NOT NULL DEFAULT 0,
    `create_time`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`user_id`, `season_id`, `book_id`),
    CONSTRAINT `chk_mt_book_quota` CHECK (`ticket_spent` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Trần Ngọn Đuốc cho một tác phẩm trong một kỳ';

CREATE TABLE IF NOT EXISTS `gamification_book_block`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `book_id`      BIGINT       NOT NULL,
    `active`       TINYINT(1)   NOT NULL DEFAULT 1,
    `reason`       VARCHAR(255) NOT NULL,
    `operator_id`  BIGINT       NOT NULL,
    `create_time`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gamification_block_book` (`book_id`),
    KEY `idx_gamification_block_active` (`active`, `book_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Khoá tác phẩm khỏi gamification khi đang xử lý gian lận hoặc bản quyền';

-- ---------------------------------------------------------------------------
-- D. Kỳ xếp hạng và snapshot
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `monthly_ticket_season`
(
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT,
    `period_code`        CHAR(7)      NOT NULL COMMENT 'Dạng 2026-08',
    `season_type`        VARCHAR(24)  NOT NULL DEFAULT 'REGULAR' COMMENT 'Dự phòng cho kỳ đặc biệt ở P2',
    `zone_id`            VARCHAR(64)  NOT NULL DEFAULT 'Asia/Ho_Chi_Minh' COMMENT 'Lưu để kết quả tái tạo được sau khi đổi cấu hình',
    `start_at`           DATETIME(3)  NOT NULL,
    `end_at`             DATETIME(3)  NOT NULL COMMENT 'Biên phải, không bao gồm',
    `vote_cutoff_at`     DATETIME(3)  NOT NULL,
    `status`             VARCHAR(16)  NOT NULL DEFAULT 'OPEN',
    `policy_version`     VARCHAR(32)  NOT NULL,
    `reward_campaign_id` BIGINT                DEFAULT NULL,
    `snapshot_id`        BIGINT                DEFAULT NULL,
    `closing_at`         DATETIME(3)           DEFAULT NULL,
    `review_at`          DATETIME(3)           DEFAULT NULL,
    `finalized_at`       DATETIME(3)           DEFAULT NULL,
    `rewarded_at`        DATETIME(3)           DEFAULT NULL,
    `cancelled_at`       DATETIME(3)           DEFAULT NULL,
    `finalized_by`       BIGINT                DEFAULT NULL,
    `cancel_reason`      VARCHAR(255)          DEFAULT NULL,
    `version`            BIGINT       NOT NULL DEFAULT 0,
    `create_time`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`        DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    -- Chống hai instance cùng mở một kỳ.
    UNIQUE KEY `uk_mt_season_period` (`period_code`),
    -- Chống lệch cấu hình múi giờ tạo ra hai kỳ chồng nhau.
    UNIQUE KEY `uk_mt_season_start` (`start_at`),
    KEY `idx_mt_season_status` (`status`, `end_at`),
    CONSTRAINT `chk_mt_season_window` CHECK (`end_at` > `start_at`
        AND `vote_cutoff_at` > `start_at` AND `vote_cutoff_at` <= `end_at`),
    CONSTRAINT `chk_mt_season_status` CHECK (`status` IN
        ('OPEN', 'CLOSING', 'REVIEW', 'FINALIZED', 'REWARDED', 'CANCELLED')),
    CONSTRAINT `chk_mt_season_finalized` CHECK (`status` NOT IN ('FINALIZED', 'REWARDED')
        OR (`snapshot_id` IS NOT NULL AND `finalized_at` IS NOT NULL)),
    CONSTRAINT `chk_mt_season_cancelled` CHECK (`status` <> 'CANCELLED'
        OR (`cancel_reason` IS NOT NULL AND `cancelled_at` IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Kỳ xếp hạng tháng với máy trạng thái OPEN đến REWARDED';

CREATE TABLE IF NOT EXISTS `monthly_rank_counter`
(
    `season_id`            BIGINT      NOT NULL,
    `book_id`              BIGINT      NOT NULL,
    `total_tickets`        BIGINT      NOT NULL DEFAULT 0,
    `vote_count`           BIGINT      NOT NULL DEFAULT 0,
    `distinct_voter_count` BIGINT      NOT NULL DEFAULT 0,
    `last_vote_at`         DATETIME(3)          DEFAULT NULL,
    `version`              BIGINT      NOT NULL DEFAULT 0,
    `create_time`          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`season_id`, `book_id`),
    KEY `idx_mt_rank_counter_board` (`season_id`, `total_tickets`, `distinct_voter_count`, `last_vote_at`),
    CONSTRAINT `chk_mt_rank_counter` CHECK (`total_tickets` >= 0 AND `vote_count` >= 0
        AND `distinct_voter_count` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Bộ đếm xếp hạng thời gian thực; chỉ để hiển thị, không phải nguồn kết quả';

CREATE TABLE IF NOT EXISTS `monthly_rank_voter`
(
    `season_id`   BIGINT      NOT NULL,
    `book_id`     BIGINT      NOT NULL,
    `user_id`     BIGINT      NOT NULL,
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`season_id`, `book_id`, `user_id`)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Đếm người bỏ phiếu duy nhất bằng INSERT IGNORE; phục vụ tiêu chí tie-break thứ hai';

CREATE TABLE IF NOT EXISTS `monthly_rank_snapshot`
(
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT,
    `season_id`              BIGINT       NOT NULL,
    `sequence_no`            INT          NOT NULL DEFAULT 1,
    `status`                 VARCHAR(16)  NOT NULL DEFAULT 'BUILDING',
    `cutoff_at`              DATETIME(3)  NOT NULL,
    `entry_count`            INT          NOT NULL DEFAULT 0,
    `total_tickets`          BIGINT       NOT NULL DEFAULT 0,
    `content_hash`           CHAR(64)              DEFAULT NULL,
    `supersedes_snapshot_id` BIGINT                DEFAULT NULL,
    `sealed_at`              DATETIME(3)           DEFAULT NULL,
    `create_time`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mt_snapshot_season_sequence` (`season_id`, `sequence_no`),
    CONSTRAINT `chk_mt_snapshot_status` CHECK (`status` IN ('BUILDING', 'SEALED', 'SUPERSEDED')),
    CONSTRAINT `chk_mt_snapshot_sealed` CHECK (`status` <> 'SEALED'
        OR (`content_hash` IS NOT NULL AND `sealed_at` IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Ảnh chụp xếp hạng bất biến sau khi niêm phong';

CREATE TABLE IF NOT EXISTS `monthly_rank_entry`
(
    `id`                   BIGINT      NOT NULL AUTO_INCREMENT,
    `snapshot_id`          BIGINT      NOT NULL,
    `rank_no`              INT         NOT NULL,
    `book_id`              BIGINT      NOT NULL,
    `author_id`            BIGINT               DEFAULT NULL,
    `total_tickets`        BIGINT      NOT NULL,
    `distinct_voter_count` BIGINT      NOT NULL,
    `last_vote_at`         DATETIME(3)          DEFAULT NULL,
    `create_time`          DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_mt_entry_snapshot_book` (`snapshot_id`, `book_id`),
    UNIQUE KEY `uk_mt_entry_snapshot_rank` (`snapshot_id`, `rank_no`),
    CONSTRAINT `chk_mt_entry_rank` CHECK (`rank_no` > 0),
    CONSTRAINT `chk_mt_entry_tickets` CHECK (`total_tickets` >= 0 AND `distinct_voter_count` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Dòng xếp hạng bất biến thuộc một snapshot';

CREATE TABLE IF NOT EXISTS `scheduled_job_run`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `job_type`        VARCHAR(48)  NOT NULL,
    `scope_type`      VARCHAR(16)  NOT NULL,
    `scope_key`       VARCHAR(64)  NOT NULL,
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'RUNNING',
    `attempt`         INT          NOT NULL DEFAULT 1,
    `checkpoint`      VARCHAR(255)          DEFAULT NULL,
    `processed_count` BIGINT       NOT NULL DEFAULT 0,
    `owner_instance`  VARCHAR(64)  NOT NULL,
    `heartbeat_at`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `started_at`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `finished_at`     DATETIME(3)           DEFAULT NULL,
    `error_message`   VARCHAR(500)          DEFAULT NULL,
    `version`         BIGINT       NOT NULL DEFAULT 0,
    `create_time`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    -- Khoá chống chạy trùng giữa nhiều instance ứng dụng.
    UNIQUE KEY `uk_job_run_scope` (`job_type`, `scope_type`, `scope_key`),
    -- Tìm job treo khi thời hạn thuê đã hết.
    KEY `idx_job_run_status` (`status`, `heartbeat_at`),
    CONSTRAINT `chk_job_run_status` CHECK (`status` IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'PAUSED')),
    CONSTRAINT `chk_job_run_scope_type` CHECK (`scope_type` IN ('SEASON', 'DATE', 'GLOBAL')),
    CONSTRAINT `chk_job_run_finished` CHECK (`status` = 'RUNNING' OR `finished_at` IS NOT NULL),
    CONSTRAINT `chk_job_run_attempt` CHECK (`attempt` >= 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Sổ chạy job theo lịch với cơ chế thuê và điểm kiểm tra để chạy lại an toàn';

-- CREATE TABLE IF NOT EXISTS không thay constraint trên database đã chạy bản migration trong
-- giai đoạn rollout. Thay lại check theo tên cố định để việc nâng cấp và chạy lặp đều nhận PAUSED.
ALTER TABLE `scheduled_job_run`
    DROP CHECK `chk_job_run_status`;
ALTER TABLE `scheduled_job_run`
    ADD CONSTRAINT `chk_job_run_status`
        CHECK (`status` IN ('RUNNING', 'SUCCEEDED', 'FAILED', 'PAUSED'));

-- ---------------------------------------------------------------------------
-- E. Quỹ thưởng tác giả
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `reward_fund_campaign`
(
    `id`              BIGINT       NOT NULL AUTO_INCREMENT,
    `period_code`     CHAR(7)      NOT NULL,
    `status`          VARCHAR(16)  NOT NULL DEFAULT 'DRAFT',
    `enabled`         TINYINT(1)   NOT NULL DEFAULT 0,
    `budget_xu`       BIGINT       NOT NULL DEFAULT 0,
    `structure_json`  JSON                  DEFAULT NULL COMMENT 'Cơ cấu giải được chụp lúc phê duyệt',
    `policy_version`  VARCHAR(32)  NOT NULL,
    `approved_by`     BIGINT                DEFAULT NULL,
    `approved_at`     DATETIME(3)           DEFAULT NULL,
    `version`         BIGINT       NOT NULL DEFAULT 0,
    `create_time`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`     DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reward_campaign_period` (`period_code`),
    CONSTRAINT `chk_reward_campaign_status` CHECK (`status` IN ('DRAFT', 'APPROVED', 'CLOSED')),
    CONSTRAINT `chk_reward_campaign_budget` CHECK (`budget_xu` >= 0),
    -- Chiến dịch đã duyệt phải truy được người duyệt; đây là một nửa của nguyên tắc bốn mắt.
    CONSTRAINT `chk_reward_campaign_approved` CHECK (`status` <> 'APPROVED'
        OR (`approved_by` IS NOT NULL AND `approved_at` IS NOT NULL AND `structure_json` IS NOT NULL))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Ngân sách và cơ cấu giải cho một kỳ; tỷ lệ được chụp lúc duyệt, không đọc lại cấu hình';

CREATE TABLE IF NOT EXISTS `author_reward_allocation`
(
    `id`                     BIGINT       NOT NULL AUTO_INCREMENT,
    `allocation_no`          VARCHAR(96)  NOT NULL COMMENT 'Dạng period:bookId:rank:authorId',
    `campaign_id`            BIGINT       NOT NULL,
    `season_id`              BIGINT       NOT NULL,
    `snapshot_id`            BIGINT       NOT NULL,
    `book_id`                BIGINT       NOT NULL,
    `author_id`              BIGINT       NOT NULL,
    `rank_no`                INT          NOT NULL,
    `amount_xu`              BIGINT       NOT NULL DEFAULT 0,
    `rounding_adjustment_xu` BIGINT       NOT NULL DEFAULT 0,
    `status`                 VARCHAR(32)  NOT NULL DEFAULT 'CALCULATED',
    `posted_at`              DATETIME(3)           DEFAULT NULL,
    `released_at`            DATETIME(3)           DEFAULT NULL,
    `clawed_back_at`         DATETIME(3)           DEFAULT NULL,
    `reason`                 VARCHAR(255)          DEFAULT NULL,
    `policy_version`         VARCHAR(32)  NOT NULL,
    `version`                BIGINT       NOT NULL DEFAULT 0,
    `create_time`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`            DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reward_allocation_no` (`allocation_no`),
    -- Một tác phẩm chỉ có một phần thưởng trong một kỳ, kể cả khi job chạy lại.
    UNIQUE KEY `uk_reward_allocation_season_book` (`season_id`, `book_id`),
    KEY `idx_reward_allocation_author` (`author_id`, `status`),
    KEY `idx_reward_allocation_release` (`status`, `posted_at`),
    CONSTRAINT `chk_reward_allocation_amount` CHECK (`amount_xu` >= 0),
    CONSTRAINT `chk_reward_allocation_rank` CHECK (`rank_no` > 0),
    CONSTRAINT `chk_reward_allocation_status` CHECK (`status` IN
        ('CALCULATED', 'APPROVED', 'POSTED_PENDING', 'RELEASED', 'CLAWED_BACK', 'SKIPPED_DUPLICATE_AUTHOR')),
    CONSTRAINT `chk_reward_allocation_skipped` CHECK (`status` <> 'SKIPPED_DUPLICATE_AUTHOR'
        OR `amount_xu` = 0),
    CONSTRAINT `chk_reward_allocation_posted` CHECK (`status` NOT IN ('POSTED_PENDING', 'RELEASED')
        OR `posted_at` IS NOT NULL),
    CONSTRAINT `chk_reward_allocation_released` CHECK (`status` <> 'RELEASED' OR `released_at` IS NOT NULL)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Phân bổ thưởng theo tác phẩm; đi qua ví clearing trước khi vào ví doanh thu tác giả';

-- ---------------------------------------------------------------------------
-- F. Sổ sự kiện, tiến trình, nhiệm vụ và cảnh giới
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS `gamification_event`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `event_type`     VARCHAR(48)  NOT NULL,
    `source_key`     VARCHAR(160) NOT NULL,
    `user_id`        BIGINT       NOT NULL,
    `book_id`        BIGINT                DEFAULT NULL,
    `occurred_at`    DATETIME(3)  NOT NULL,
    `local_date`     DATE         NOT NULL COMMENT 'Tính ở Java theo múi giờ cấu hình, không dùng CURDATE()',
    `payload_hash`   CHAR(64)     NOT NULL,
    `payload`        JSON                  DEFAULT NULL COMMENT 'Không chứa PII, token hay dữ liệu thanh toán',
    `status`         VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    `attempt`        INT          NOT NULL DEFAULT 0,
    `processed_at`   DATETIME(3)           DEFAULT NULL,
    `error_message`  VARCHAR(500)          DEFAULT NULL,
    `policy_version` VARCHAR(32)  NOT NULL,
    `version`        BIGINT       NOT NULL DEFAULT 0,
    `create_time`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    -- Hàng rào chống phát lại. Tầng service dùng INSERT IGNORE; affected = 0 nghĩa là đã có.
    UNIQUE KEY `uk_gamification_event_source` (`source_key`),
    KEY `idx_gamification_event_claim` (`status`, `id`),
    KEY `idx_gamification_event_user_date` (`user_id`, `local_date`, `event_type`),
    KEY `idx_gamification_event_retry` (`status`, `attempt`, `processed_at`),
    CONSTRAINT `chk_gamification_event_status` CHECK (`status` IN
        ('PENDING', 'PROCESSED', 'SKIPPED', 'FAILED')),
    CONSTRAINT `chk_gamification_event_attempt` CHECK (`attempt` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Sự kiện nghiệp vụ; ghi trong transaction nguồn, áp dụng ở transaction riêng của worker';

CREATE TABLE IF NOT EXISTS `gamification_profile`
(
    `id`                BIGINT      NOT NULL AUTO_INCREMENT,
    `user_id`           BIGINT      NOT NULL,
    `level`             INT         NOT NULL DEFAULT 1,
    `total_exp`         BIGINT      NOT NULL DEFAULT 0,
    `rule_version`      VARCHAR(32) NOT NULL DEFAULT 'v1',
    `realm_code`        VARCHAR(32)          DEFAULT NULL,
    `frame_code`        VARCHAR(32)          DEFAULT NULL,
    `checkin_streak`    INT         NOT NULL DEFAULT 0,
    `longest_streak`    INT         NOT NULL DEFAULT 0,
    `last_checkin_date` DATE                 DEFAULT NULL,
    `realm_changed_at`  DATETIME(3)          DEFAULT NULL,
    `ticker_opt_out`    TINYINT(1)  NOT NULL DEFAULT 1 COMMENT 'Mặc định không hiện trên bảng chạy; tham gia phải chọn tường minh',
    `version`           BIGINT      NOT NULL DEFAULT 0,
    `create_time`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`       DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_gamification_profile_user` (`user_id`),
    CONSTRAINT `chk_gamification_profile_level` CHECK (`level` >= 1),
    CONSTRAINT `chk_gamification_profile_exp` CHECK (`total_exp` >= 0),
    CONSTRAINT `chk_gamification_profile_streak` CHECK (`checkin_streak` >= 0 AND `longest_streak` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Hồ sơ gamification tạo lười khi dùng lần đầu; không backfill toàn bộ người dùng';

CREATE TABLE IF NOT EXISTS `gamification_profile_audit`
(
    `id`            BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`       BIGINT       NOT NULL,
    `change_type`   VARCHAR(24)  NOT NULL,
    `from_value`    VARCHAR(64)           DEFAULT NULL,
    `to_value`      VARCHAR(64)           DEFAULT NULL,
    `operator_type` VARCHAR(16)  NOT NULL DEFAULT 'USER',
    `operator_id`   BIGINT                DEFAULT NULL,
    `reason`        VARCHAR(255)          DEFAULT NULL,
    `create_time`   DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    KEY `idx_gamification_profile_audit_user` (`user_id`, `id`),
    CONSTRAINT `chk_gamification_audit_type` CHECK (`change_type` IN ('REALM', 'FRAME', 'TICKER_OPT')),
    CONSTRAINT `chk_gamification_audit_operator` CHECK (`operator_type` IN ('USER', 'ADMIN', 'SYSTEM'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Nhật ký đổi cảnh giới và tuỳ chọn hiển thị, bất biến';

CREATE TABLE IF NOT EXISTS `user_exp_ledger`
(
    `id`             BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`        BIGINT       NOT NULL,
    `source_key`     VARCHAR(160) NOT NULL,
    `source_type`    VARCHAR(32)  NOT NULL,
    `amount`         BIGINT       NOT NULL COMMENT 'Có dấu; cho phép âm khi điều chỉnh',
    `balance_after`  BIGINT       NOT NULL,
    `rule_version`   VARCHAR(32)  NOT NULL,
    `policy_version` VARCHAR(32)  NOT NULL,
    `create_time`    DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_user_exp_source` (`source_key`),
    KEY `idx_user_exp_user` (`user_id`, `id`),
    CONSTRAINT `chk_user_exp_amount` CHECK (`amount` <> 0),
    CONSTRAINT `chk_user_exp_balance` CHECK (`balance_after` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Sổ EXP bất biến; cấp độ được tính lại theo rule_version của từng bút toán';

CREATE TABLE IF NOT EXISTS `level_rule`
(
    `id`           BIGINT       NOT NULL AUTO_INCREMENT,
    `rule_version` VARCHAR(32)  NOT NULL,
    `level`        INT          NOT NULL,
    `min_exp`      BIGINT       NOT NULL,
    `title_key`    VARCHAR(64)  NOT NULL,
    `frame_code`   VARCHAR(32)           DEFAULT NULL,
    `create_time`  DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_level_rule_version_level` (`rule_version`, `level`),
    UNIQUE KEY `uk_level_rule_version_exp` (`rule_version`, `min_exp`),
    CONSTRAINT `chk_level_rule_level` CHECK (`level` >= 1),
    CONSTRAINT `chk_level_rule_exp` CHECK (`min_exp` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Ngưỡng cấp độ theo phiên bản quy tắc; đổi quy tắc là thêm phiên bản mới, không sửa dòng cũ';

CREATE TABLE IF NOT EXISTS `realm_catalog`
(
    `id`          BIGINT      NOT NULL AUTO_INCREMENT,
    `realm_code`  VARCHAR(32) NOT NULL,
    `name_key`    VARCHAR(64) NOT NULL,
    `min_level`   INT         NOT NULL DEFAULT 1,
    `sort_no`     INT         NOT NULL DEFAULT 0,
    `active`      TINYINT(1)  NOT NULL DEFAULT 1,
    `create_time` DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_realm_catalog_code` (`realm_code`),
    CONSTRAINT `chk_realm_catalog_level` CHECK (`min_level` >= 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Danh mục cảnh giới; thuần danh xưng, không ảnh hưởng quyền lợi ở phiên bản v1';

CREATE TABLE IF NOT EXISTS `quest_definition`
(
    `id`           BIGINT      NOT NULL AUTO_INCREMENT,
    `quest_code`   VARCHAR(48) NOT NULL,
    `event_type`   VARCHAR(48) NOT NULL,
    `period_type`  VARCHAR(16) NOT NULL,
    `target_count` INT         NOT NULL DEFAULT 1,
    `name_key`     VARCHAR(64) NOT NULL,
    `active`       TINYINT(1)  NOT NULL DEFAULT 1,
    `sort_no`      INT         NOT NULL DEFAULT 0,
    `create_time`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quest_definition_code` (`quest_code`),
    CONSTRAINT `chk_quest_definition_period` CHECK (`period_type` IN ('DAILY', 'WEEKLY', 'ONE_TIME')),
    CONSTRAINT `chk_quest_definition_target` CHECK (`target_count` >= 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Danh mục nhiệm vụ';

CREATE TABLE IF NOT EXISTS `quest_campaign`
(
    `id`             BIGINT      NOT NULL AUTO_INCREMENT,
    `campaign_code`  VARCHAR(48) NOT NULL,
    `start_at`       DATETIME(3) NOT NULL,
    `end_at`         DATETIME(3) NOT NULL,
    `status`         VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    `policy_version` VARCHAR(32) NOT NULL,
    `create_time`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`    DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quest_campaign_code` (`campaign_code`),
    KEY `idx_quest_campaign_window` (`status`, `start_at`, `end_at`),
    CONSTRAINT `chk_quest_campaign_window` CHECK (`end_at` > `start_at`),
    CONSTRAINT `chk_quest_campaign_status` CHECK (`status` IN ('DRAFT', 'ACTIVE', 'CLOSED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Chiến dịch nhiệm vụ theo khoảng thời gian';

CREATE TABLE IF NOT EXISTS `quest_reward`
(
    `id`            BIGINT      NOT NULL AUTO_INCREMENT,
    `quest_code`    VARCHAR(48) NOT NULL,
    `campaign_code` VARCHAR(48) NOT NULL DEFAULT 'DEFAULT',
    `reward_type`   VARCHAR(16) NOT NULL,
    `amount`        BIGINT      NOT NULL,
    `create_time`   DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_quest_reward_scope` (`quest_code`, `campaign_code`, `reward_type`),
    CONSTRAINT `chk_quest_reward_type` CHECK (`reward_type` IN ('EXP', 'TICKET')),
    CONSTRAINT `chk_quest_reward_amount` CHECK (`amount` > 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Phần thưởng của nhiệm vụ theo chiến dịch';

CREATE TABLE IF NOT EXISTS `user_quest_progress`
(
    `user_id`      BIGINT      NOT NULL,
    `quest_code`   VARCHAR(48) NOT NULL,
    `period_key`   VARCHAR(16) NOT NULL COMMENT 'DAILY dùng yyyy-MM-dd, WEEKLY dùng yyyy-Www, ONE_TIME dùng ALL',
    `current_count` INT        NOT NULL DEFAULT 0,
    `target_count` INT         NOT NULL,
    `completed_at` DATETIME(3)          DEFAULT NULL,
    `version`      BIGINT      NOT NULL DEFAULT 0,
    `create_time`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`  DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`user_id`, `quest_code`, `period_key`),
    CONSTRAINT `chk_user_quest_progress` CHECK (`current_count` >= 0 AND `target_count` >= 1)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Tiến độ nhiệm vụ theo chu kỳ';

CREATE TABLE IF NOT EXISTS `quest_claim`
(
    `id`               BIGINT       NOT NULL AUTO_INCREMENT,
    `user_id`          BIGINT       NOT NULL,
    `quest_code`       VARCHAR(48)  NOT NULL,
    `period_key`       VARCHAR(16)  NOT NULL,
    `campaign_code`    VARCHAR(48)  NOT NULL DEFAULT 'DEFAULT',
    `exp_amount`       BIGINT       NOT NULL DEFAULT 0,
    `ticket_amount`    BIGINT       NOT NULL DEFAULT 0,
    `exp_ledger_id`    BIGINT                DEFAULT NULL,
    `ticket_ledger_id` BIGINT                DEFAULT NULL,
    `idempotency_key`  VARCHAR(128) NOT NULL,
    `policy_version`   VARCHAR(32)  NOT NULL,
    `create_time`      DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    -- Một nhiệm vụ trong một chu kỳ chỉ nhận thưởng đúng một lần.
    UNIQUE KEY `uk_quest_claim_scope` (`user_id`, `quest_code`, `period_key`),
    UNIQUE KEY `uk_quest_claim_idempotency` (`idempotency_key`),
    CONSTRAINT `chk_quest_claim_amount` CHECK (`exp_amount` >= 0 AND `ticket_amount` >= 0
        AND (`exp_amount` > 0 OR `ticket_amount` > 0))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Lần nhận thưởng nhiệm vụ, bất biến và truy ngược được tới bút toán EXP và Đuốc';

-- Heartbeat đọc không dùng bảng analytics ẩn danh. Session và counter là projection có khoá;
-- receipt là bằng chứng bất biến để cùng sequence/cùng payload trả kết quả cũ, payload khác bị chặn.
CREATE TABLE IF NOT EXISTS `reading_session`
(
    `session_id`             VARCHAR(64) NOT NULL,
    `user_id`                BIGINT      NOT NULL,
    `book_id`                BIGINT      NOT NULL,
    `book_index_id`          BIGINT      NOT NULL,
    `last_sequence`          INT         NOT NULL DEFAULT -1,
    `last_heartbeat_at`      DATETIME(3) NOT NULL,
    `total_accepted_seconds` INT         NOT NULL DEFAULT 0,
    `status`                 VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    `version`                BIGINT      NOT NULL DEFAULT 0,
    `create_time`            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`            DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`session_id`),
    KEY `idx_reading_session_user` (`user_id`, `update_time`, `session_id`),
    CONSTRAINT `chk_reading_session_sequence` CHECK (`last_sequence` >= -1),
    CONSTRAINT `chk_reading_session_seconds` CHECK (`total_accepted_seconds` >= 0),
    CONSTRAINT `chk_reading_session_status` CHECK (`status` IN ('ACTIVE', 'CLOSED'))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Phiên đọc xác thực; thời gian server là trần cho tín hiệu foreground từ client';

CREATE TABLE IF NOT EXISTS `reading_daily_counter`
(
    `user_id`          BIGINT      NOT NULL,
    `local_date`       DATE        NOT NULL,
    `verified_seconds` INT         NOT NULL DEFAULT 0,
    `version`          BIGINT      NOT NULL DEFAULT 0,
    `create_time`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    `update_time`      DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`user_id`, `local_date`),
    KEY `idx_reading_daily_date` (`local_date`, `user_id`),
    CONSTRAINT `chk_reading_daily_seconds` CHECK (`verified_seconds` >= 0)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Projection thời gian đọc đã xác minh theo ngày địa phương';

CREATE TABLE IF NOT EXISTS `reading_heartbeat_receipt`
(
    `id`                  BIGINT       NOT NULL AUTO_INCREMENT,
    `session_id`          VARCHAR(64)  NOT NULL,
    `user_id`             BIGINT       NOT NULL,
    `book_id`             BIGINT       NOT NULL,
    `book_index_id`       BIGINT       NOT NULL,
    `sequence_no`         INT          NOT NULL,
    `active_seconds`      INT          NOT NULL,
    `accepted_seconds`    INT          NOT NULL,
    `daily_seconds_after` INT          NOT NULL,
    `first_minute_bucket` INT                   DEFAULT NULL,
    `event_count`         INT          NOT NULL DEFAULT 0,
    `local_date`          DATE         NOT NULL,
    `request_hash`        CHAR(64)     NOT NULL,
    `heartbeat_at`        DATETIME(3)  NOT NULL,
    `policy_version`      VARCHAR(32)  NOT NULL,
    `create_time`         DATETIME(3)  NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_reading_heartbeat_sequence` (`session_id`, `sequence_no`),
    KEY `idx_reading_heartbeat_user_date` (`user_id`, `local_date`, `id`),
    CONSTRAINT `chk_reading_heartbeat_sequence` CHECK (`sequence_no` >= 0),
    CONSTRAINT `chk_reading_heartbeat_seconds` CHECK (`active_seconds` >= 0
        AND `accepted_seconds` >= 0 AND `accepted_seconds` <= `active_seconds`
        AND `daily_seconds_after` >= `accepted_seconds`),
    CONSTRAINT `chk_reading_heartbeat_events` CHECK (`event_count` >= 0
        AND ((`event_count` = 0 AND `first_minute_bucket` IS NULL)
            OR (`event_count` > 0 AND `first_minute_bucket` >= 1)))
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = 'Biên nhận heartbeat bất biến; trọng tài idempotency theo session và sequence';

-- ---------------------------------------------------------------------------
-- G. Trigger bất biến
-- ---------------------------------------------------------------------------
-- Trigger bán bất biến dùng toán tử so sánh an toàn với NULL `<=>`. Viết `NEW.x <> OLD.x` sẽ trả
-- về NULL khi một trong hai vế là NULL, mệnh đề IF không chạy, và hàng rào bị thủng đúng ở các
-- cột nullable là những cột dễ bị sửa lén nhất.

DROP TRIGGER IF EXISTS `trg_monthly_ticket_ledger_no_update`;
DELIMITER //
CREATE TRIGGER `trg_monthly_ticket_ledger_no_update`
    BEFORE UPDATE ON `monthly_ticket_ledger`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'monthly_ticket_ledger is immutable';
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_monthly_ticket_ledger_no_delete`;
DELIMITER //
CREATE TRIGGER `trg_monthly_ticket_ledger_no_delete`
    BEFORE DELETE ON `monthly_ticket_ledger`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'monthly_ticket_ledger is immutable';
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_monthly_ticket_alloc_no_update`;
DELIMITER //
CREATE TRIGGER `trg_monthly_ticket_alloc_no_update`
    BEFORE UPDATE ON `monthly_ticket_lot_allocation`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'monthly_ticket_lot_allocation is immutable';
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_monthly_ticket_alloc_no_delete`;
DELIMITER //
CREATE TRIGGER `trg_monthly_ticket_alloc_no_delete`
    BEFORE DELETE ON `monthly_ticket_lot_allocation`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'monthly_ticket_lot_allocation is immutable';
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_user_exp_ledger_no_update`;
DELIMITER //
CREATE TRIGGER `trg_user_exp_ledger_no_update`
    BEFORE UPDATE ON `user_exp_ledger`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'user_exp_ledger is immutable';
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_user_exp_ledger_no_delete`;
DELIMITER //
CREATE TRIGGER `trg_user_exp_ledger_no_delete`
    BEFORE DELETE ON `user_exp_ledger`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'user_exp_ledger is immutable';
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_quest_claim_no_update`;
DELIMITER //
CREATE TRIGGER `trg_quest_claim_no_update`
    BEFORE UPDATE ON `quest_claim`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'quest_claim is immutable';
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_quest_claim_no_delete`;
DELIMITER //
CREATE TRIGGER `trg_quest_claim_no_delete`
    BEFORE DELETE ON `quest_claim`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'quest_claim is immutable';
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_reading_heartbeat_receipt_no_update`;
DELIMITER //
CREATE TRIGGER `trg_reading_heartbeat_receipt_no_update`
    BEFORE UPDATE ON `reading_heartbeat_receipt`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reading_heartbeat_receipt is immutable';
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_reading_heartbeat_receipt_no_delete`;
DELIMITER //
CREATE TRIGGER `trg_reading_heartbeat_receipt_no_delete`
    BEFORE DELETE ON `reading_heartbeat_receipt`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'reading_heartbeat_receipt is immutable';
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_monthly_rank_entry_no_update`;
DELIMITER //
CREATE TRIGGER `trg_monthly_rank_entry_no_update`
    BEFORE UPDATE ON `monthly_rank_entry`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'monthly_rank_entry is immutable';
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_monthly_rank_entry_no_delete`;
DELIMITER //
CREATE TRIGGER `trg_monthly_rank_entry_no_delete`
    BEFORE DELETE ON `monthly_rank_entry`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'monthly_rank_entry is immutable';
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_gamification_profile_audit_no_update`;
DELIMITER //
CREATE TRIGGER `trg_gamification_profile_audit_no_update`
    BEFORE UPDATE ON `gamification_profile_audit`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification_profile_audit is immutable';
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_gamification_profile_audit_no_delete`;
DELIMITER //
CREATE TRIGGER `trg_gamification_profile_audit_no_delete`
    BEFORE DELETE ON `gamification_profile_audit`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification_profile_audit is immutable';
END//
DELIMITER ;

-- Lot cho phép đổi số dư còn lại và trạng thái, nhưng các cột định danh nguồn gốc thì không.
DROP TRIGGER IF EXISTS `trg_monthly_ticket_lot_immutable_columns`;
DELIMITER //
CREATE TRIGGER `trg_monthly_ticket_lot_immutable_columns`
    BEFORE UPDATE ON `monthly_ticket_lot`
    FOR EACH ROW
BEGIN
    IF NOT (NEW.`user_id` <=> OLD.`user_id`)
        OR NOT (NEW.`granted_amount` <=> OLD.`granted_amount`)
        OR NOT (NEW.`grant_ledger_id` <=> OLD.`grant_ledger_id`)
        OR NOT (NEW.`source_type` <=> OLD.`source_type`)
        OR NOT (NEW.`source_ref` <=> OLD.`source_ref`)
        OR NOT (NEW.`effective_at` <=> OLD.`effective_at`)
        OR NOT (NEW.`expire_at` <=> OLD.`expire_at`)
        OR NOT (NEW.`policy_version` <=> OLD.`policy_version`) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'monthly_ticket_lot origin columns are immutable';
    END IF;
    IF OLD.`status` <> 'ACTIVE'
        AND (NOT (NEW.`status` <=> OLD.`status`)
            OR NOT (NEW.`remaining_amount` <=> OLD.`remaining_amount`)
            OR NOT (NEW.`closed_at` <=> OLD.`closed_at`)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'closed monthly_ticket_lot cannot be reopened or changed';
    END IF;
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_monthly_ticket_lot_no_delete`;
DELIMITER //
CREATE TRIGGER `trg_monthly_ticket_lot_no_delete`
    BEFORE DELETE ON `monthly_ticket_lot`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'monthly_ticket_lot cannot be deleted';
END//
DELIMITER ;

-- Phiếu chỉ được chuyển sang trạng thái huỷ; nội dung phiếu không được sửa.
DROP TRIGGER IF EXISTS `trg_monthly_ticket_vote_immutable_columns`;
DELIMITER //
CREATE TRIGGER `trg_monthly_ticket_vote_immutable_columns`
    BEFORE UPDATE ON `monthly_ticket_vote`
    FOR EACH ROW
BEGIN
    IF NOT (NEW.`season_id` <=> OLD.`season_id`)
        OR NOT (NEW.`book_id` <=> OLD.`book_id`)
        OR NOT (NEW.`author_id` <=> OLD.`author_id`)
        OR NOT (NEW.`user_id` <=> OLD.`user_id`)
        OR NOT (NEW.`ticket_count` <=> OLD.`ticket_count`)
        OR NOT (NEW.`ledger_id` <=> OLD.`ledger_id`)
        OR NOT (NEW.`idempotency_key` <=> OLD.`idempotency_key`)
        OR NOT (NEW.`request_hash` <=> OLD.`request_hash`)
        OR NOT (NEW.`client_request_id` <=> OLD.`client_request_id`)
        OR NOT (NEW.`source_ip_hash` <=> OLD.`source_ip_hash`)
        OR NOT (NEW.`policy_version` <=> OLD.`policy_version`) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'monthly_ticket_vote content is immutable';
    END IF;
    IF OLD.`status` = 'VOIDED'
        AND (NOT (NEW.`status` <=> OLD.`status`)
            OR NOT (NEW.`voided_ledger_id` <=> OLD.`voided_ledger_id`)
            OR NOT (NEW.`void_reason` <=> OLD.`void_reason`)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'voided monthly_ticket_vote cannot be restored or changed';
    END IF;
    IF OLD.`status` = 'VALID' AND NEW.`status` = 'VALID'
        AND (NOT (NEW.`voided_ledger_id` <=> OLD.`voided_ledger_id`)
            OR NOT (NEW.`void_reason` <=> OLD.`void_reason`)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'monthly_ticket_vote void details require VOIDED status';
    END IF;
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_monthly_ticket_vote_no_delete`;
DELIMITER //
CREATE TRIGGER `trg_monthly_ticket_vote_no_delete`
    BEFORE DELETE ON `monthly_ticket_vote`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'monthly_ticket_vote cannot be deleted';
END//
DELIMITER ;

-- Hàng rào ở tầng lưu trữ cho yêu cầu "kết quả không đổi sau khi chốt".
DROP TRIGGER IF EXISTS `trg_monthly_ticket_season_no_backward`;
DELIMITER //
CREATE TRIGGER `trg_monthly_ticket_season_no_backward`
    BEFORE UPDATE ON `monthly_ticket_season`
    FOR EACH ROW
BEGIN
    IF NOT (NEW.`status` <=> OLD.`status`)
        AND NOT ((OLD.`status` = 'OPEN' AND NEW.`status` IN ('CLOSING', 'CANCELLED'))
            OR (OLD.`status` = 'CLOSING' AND NEW.`status` IN ('REVIEW', 'CANCELLED'))
            OR (OLD.`status` = 'REVIEW' AND NEW.`status` IN ('FINALIZED', 'CANCELLED'))
            OR (OLD.`status` = 'FINALIZED' AND NEW.`status` = 'REWARDED')) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'invalid monthly_ticket_season status transition';
    END IF;
    IF OLD.`status` <> 'OPEN'
        AND (NOT (NEW.`period_code` <=> OLD.`period_code`)
            OR NOT (NEW.`season_type` <=> OLD.`season_type`)
            OR NOT (NEW.`start_at` <=> OLD.`start_at`)
            OR NOT (NEW.`end_at` <=> OLD.`end_at`)
            OR NOT (NEW.`vote_cutoff_at` <=> OLD.`vote_cutoff_at`)
            OR NOT (NEW.`zone_id` <=> OLD.`zone_id`)
            OR NOT (NEW.`policy_version` <=> OLD.`policy_version`)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'monthly_ticket_season window is immutable once closing';
    END IF;
END//
DELIMITER ;

-- Snapshot đã niêm phong không được ghi đè; sửa kết quả phải bằng snapshot mới có supersedes.
DROP TRIGGER IF EXISTS `trg_monthly_rank_snapshot_sealed`;
DELIMITER //
CREATE TRIGGER `trg_monthly_rank_snapshot_sealed`
    BEFORE UPDATE ON `monthly_rank_snapshot`
    FOR EACH ROW
BEGIN
    IF OLD.`status` = 'BUILDING' AND NEW.`status` NOT IN ('BUILDING', 'SEALED') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'monthly_rank_snapshot must be sealed before superseding';
    END IF;
    IF OLD.`status` = 'SEALED' AND NEW.`status` NOT IN ('SEALED', 'SUPERSEDED') THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'sealed monthly_rank_snapshot cannot be reopened';
    END IF;
    IF OLD.`status` = 'SUPERSEDED' AND NEW.`status` <> 'SUPERSEDED' THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'superseded monthly_rank_snapshot cannot change status';
    END IF;
    IF OLD.`status` = 'SEALED'
        AND (NOT (NEW.`content_hash` <=> OLD.`content_hash`)
            OR NOT (NEW.`entry_count` <=> OLD.`entry_count`)
            OR NOT (NEW.`total_tickets` <=> OLD.`total_tickets`)
            OR NOT (NEW.`cutoff_at` <=> OLD.`cutoff_at`)) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'monthly_rank_snapshot is immutable once sealed';
    END IF;
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_monthly_rank_snapshot_no_delete`;
DELIMITER //
CREATE TRIGGER `trg_monthly_rank_snapshot_no_delete`
    BEFORE DELETE ON `monthly_rank_snapshot`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'monthly_rank_snapshot cannot be deleted';
END//
DELIMITER ;

-- Sự kiện chỉ được đổi trạng thái xử lý; nội dung sự kiện là bằng chứng nên bất biến.
DROP TRIGGER IF EXISTS `trg_gamification_event_immutable_columns`;
DELIMITER //
CREATE TRIGGER `trg_gamification_event_immutable_columns`
    BEFORE UPDATE ON `gamification_event`
    FOR EACH ROW
BEGIN
    IF NOT (NEW.`event_type` <=> OLD.`event_type`)
        OR NOT (NEW.`source_key` <=> OLD.`source_key`)
        OR NOT (NEW.`user_id` <=> OLD.`user_id`)
        OR NOT (NEW.`book_id` <=> OLD.`book_id`)
        OR NOT (NEW.`occurred_at` <=> OLD.`occurred_at`)
        OR NOT (NEW.`local_date` <=> OLD.`local_date`)
        OR NOT (NEW.`payload_hash` <=> OLD.`payload_hash`) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'gamification_event content is immutable';
    END IF;
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_gamification_event_no_delete`;
DELIMITER //
CREATE TRIGGER `trg_gamification_event_no_delete`
    BEFORE DELETE ON `gamification_event`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'gamification_event cannot be deleted';
END//
DELIMITER ;

-- Quy tắc cấp độ được version hoá: thay đổi nghĩa là thêm rule_version mới, không sửa lịch sử.
DROP TRIGGER IF EXISTS `trg_level_rule_no_update`;
DELIMITER //
CREATE TRIGGER `trg_level_rule_no_update`
    BEFORE UPDATE ON `level_rule`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'level_rule is immutable; create a new rule_version';
END//
DELIMITER ;

DROP TRIGGER IF EXISTS `trg_level_rule_no_delete`;
DELIMITER //
CREATE TRIGGER `trg_level_rule_no_delete`
    BEFORE DELETE ON `level_rule`
    FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'level_rule is immutable; create a new rule_version';
END//
DELIMITER ;

-- ---------------------------------------------------------------------------
-- H. Dữ liệu khởi tạo
-- ---------------------------------------------------------------------------
-- Dùng WHERE NOT EXISTS trên khoá duy nhất nên chạy lại an toàn, đồng thời không đè lên dữ liệu
-- mà quản trị viên đã tuỳ chỉnh sau khi cài đặt.

INSERT INTO `level_rule` (`rule_version`, `level`, `min_exp`, `title_key`, `frame_code`)
SELECT * FROM (
    SELECT 'v1' AS rv, 1 AS lv, 0 AS me, 'gamification.level.1' AS tk, 'frame_bronze' AS fc UNION ALL
    SELECT 'v1', 2, 100, 'gamification.level.2', 'frame_bronze' UNION ALL
    SELECT 'v1', 3, 300, 'gamification.level.3', 'frame_bronze' UNION ALL
    SELECT 'v1', 4, 700, 'gamification.level.4', 'frame_silver' UNION ALL
    SELECT 'v1', 5, 1500, 'gamification.level.5', 'frame_silver' UNION ALL
    SELECT 'v1', 6, 3000, 'gamification.level.6', 'frame_silver' UNION ALL
    SELECT 'v1', 7, 6000, 'gamification.level.7', 'frame_gold' UNION ALL
    SELECT 'v1', 8, 12000, 'gamification.level.8', 'frame_gold' UNION ALL
    SELECT 'v1', 9, 24000, 'gamification.level.9', 'frame_gold' UNION ALL
    SELECT 'v1', 10, 50000, 'gamification.level.10', 'frame_diamond'
) seed
WHERE NOT EXISTS (SELECT 1 FROM `level_rule` WHERE `rule_version` = 'v1');

INSERT INTO `realm_catalog` (`realm_code`, `name_key`, `min_level`, `sort_no`, `active`)
SELECT * FROM (
    SELECT 'NHAP_MON' AS rc, 'gamification.realm.nhapMon' AS nk, 1 AS ml, 1 AS sn, 1 AS ac UNION ALL
    SELECT 'TIEN_PHONG', 'gamification.realm.tienPhong', 3, 2, 1 UNION ALL
    SELECT 'DAI_SU', 'gamification.realm.daiSu', 5, 3, 1 UNION ALL
    SELECT 'TON_GIA', 'gamification.realm.tonGia', 7, 4, 1 UNION ALL
    SELECT 'CHI_TON', 'gamification.realm.chiTon', 10, 5, 1
) seed
WHERE NOT EXISTS (SELECT 1 FROM `realm_catalog` LIMIT 1);

-- Bộ nhiệm vụ v1 chỉ dựa trên ba nguồn sự kiện tự động và đáng tin cậy. Nhiệm vụ bình luận là
-- thành tựu một lần vì mỗi người chỉ bình luận được một lần cho mỗi tác phẩm và bình luận phải
-- chờ quản trị viên duyệt thủ công; xem QĐ-6 trong doc/gamification-policy-v1.md.
INSERT INTO `quest_definition` (`quest_code`, `event_type`, `period_type`, `target_count`, `name_key`, `active`, `sort_no`)
SELECT * FROM (
    SELECT 'DAILY_CHECK_IN' AS qc, 'CHECK_IN_COMPLETED' AS et, 'DAILY' AS pt, 1 AS tc,
           'quest.dailyCheckIn' AS nk, 1 AS ac, 1 AS sn UNION ALL
    SELECT 'DAILY_READING', 'READING_MINUTE_VERIFIED', 'DAILY', 30, 'quest.dailyReading', 1, 2 UNION ALL
    SELECT 'DAILY_PAID_CHAPTER', 'CHAPTER_PURCHASED', 'DAILY', 1, 'quest.dailyPaidChapter', 1, 3 UNION ALL
    SELECT 'FIRST_COMMENT_APPROVED', 'COMMENT_APPROVED', 'ONE_TIME', 1, 'quest.firstCommentApproved', 1, 4
) seed
WHERE NOT EXISTS (SELECT 1 FROM `quest_definition` LIMIT 1);

INSERT INTO `quest_reward` (`quest_code`, `campaign_code`, `reward_type`, `amount`)
SELECT * FROM (
    SELECT 'DAILY_CHECK_IN' AS qc, 'DEFAULT' AS cc, 'EXP' AS rt, 10 AS am UNION ALL
    SELECT 'DAILY_READING', 'DEFAULT', 'EXP', 20 UNION ALL
    SELECT 'DAILY_READING', 'DEFAULT', 'TICKET', 1 UNION ALL
    SELECT 'DAILY_PAID_CHAPTER', 'DEFAULT', 'EXP', 15 UNION ALL
    SELECT 'FIRST_COMMENT_APPROVED', 'DEFAULT', 'EXP', 50
) seed
WHERE NOT EXISTS (SELECT 1 FROM `quest_reward` LIMIT 1);

-- ---------------------------------------------------------------------------
-- I. Menu và quyền quản trị
-- ---------------------------------------------------------------------------
-- Không dùng menu_id cố định để tránh đè dữ liệu quản trị tuỳ chỉnh. Quyền cấp phiếu, quyền chốt
-- kỳ và quyền duyệt tiền được tách riêng để giữ nguyên tắc bốn mắt.

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT seed.parent_id, 'Gamification', 'novel/gamification', 'novel:gamification:view',
       1, 'fa fa-trophy', 8, NOW()
FROM (
    SELECT COALESCE((
        SELECT parent_id FROM `sys_menu` WHERE perms = 'novel:author:author' LIMIT 1
    ), 0) AS parent_id
) seed
WHERE NOT EXISTS (
    SELECT 1 FROM `sys_menu` WHERE perms = 'novel:gamification:view'
);

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Cấu hình nhiệm vụ và cấp độ', NULL, 'novel:gamification:config', 2, NULL, 1, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:gamification:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:gamification:config');

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Xử lý gian lận', NULL, 'novel:gamification:review', 2, NULL, 2, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:gamification:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:gamification:review');

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Cấp Ngọn Đuốc', NULL, 'novel:gamification:grant', 2, NULL, 3, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:gamification:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:gamification:grant');

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Chốt kỳ xếp hạng', NULL, 'novel:gamification:finalize', 2, NULL, 4, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:gamification:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:gamification:finalize');

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Duyệt quỹ thưởng', NULL, 'novel:gamification:reward', 2, NULL, 5, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:gamification:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:gamification:reward');

INSERT INTO `sys_menu` (`parent_id`, `name`, `url`, `perms`, `type`, `icon`, `order_num`, `gmt_create`)
SELECT parent.menu_id, 'Điều chỉnh và thu hồi', NULL, 'novel:gamification:adjust', 2, NULL, 6, NOW()
FROM (SELECT menu_id FROM `sys_menu` WHERE perms = 'novel:gamification:view' LIMIT 1) parent
WHERE NOT EXISTS (SELECT 1 FROM `sys_menu` WHERE perms = 'novel:gamification:adjust');

INSERT INTO `sys_role_menu` (`role_id`, `menu_id`)
SELECT role.role_id, menu.menu_id
FROM `sys_role` role
JOIN `sys_menu` menu
  ON menu.perms IN ('novel:gamification:view', 'novel:gamification:config',
                    'novel:gamification:review', 'novel:gamification:grant',
                    'novel:gamification:finalize', 'novel:gamification:reward',
                    'novel:gamification:adjust')
WHERE role.role_sign = 'admin'
  AND NOT EXISTS (
      SELECT 1 FROM `sys_role_menu` relation
      WHERE relation.role_id = role.role_id AND relation.menu_id = menu.menu_id
  );
