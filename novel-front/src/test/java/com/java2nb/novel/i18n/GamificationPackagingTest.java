package com.java2nb.novel.i18n;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Kiểm tra tĩnh trên văn bản thô của migration, compose và mapper.
 *
 * <p>Khuôn mẫu lấy từ {@code ReaderStatePackagingTest}: đọc file, khẳng định chuỗi, không khởi
 * động Spring. Giá trị của kiểu test này là nó bắt được những sai sót mà unit test không thấy và
 * chỉ lộ ra khi deploy, ví dụ quên mount migration hoặc viết trigger so sánh không an toàn NULL.
 */
class GamificationPackagingTest {

    private static final String MIGRATION = "doc/sql/20260729_gamification_monthly_ticket.sql";

    private Path repository() {
        return Path.of("").toAbsolutePath().normalize().getParent();
    }

    private String read(String relativePath) throws Exception {
        return Files.readString(repository().resolve(relativePath), StandardCharsets.UTF_8);
    }

    /**
     * Bỏ khối chú thích XML trước khi khẳng định điều cấm. Nếu không, một dòng chú thích giải
     * thích vì sao không được dùng hàm nào đó sẽ tự làm đỏ chính bài kiểm tra đó.
     */
    private String withoutXmlComments(String xml) {
        return xml.replaceAll("(?s)<!--.*?-->", "");
    }

    @Test
    void migrationCreatesEveryTableTheServicesDependOn() throws Exception {
        String sql = read(MIGRATION);

        for (String table : new String[]{
            "monthly_ticket_account", "monthly_ticket_lot", "monthly_ticket_ledger",
            "monthly_ticket_lot_allocation", "monthly_ticket_vote", "monthly_ticket_daily_counter",
            "monthly_ticket_user_book_quota", "monthly_ticket_season", "monthly_rank_counter",
            "monthly_rank_voter", "monthly_rank_snapshot", "monthly_rank_entry",
            "scheduled_job_run", "reward_fund_campaign", "author_reward_allocation",
            "gamification_event", "gamification_profile", "gamification_profile_audit",
            "gamification_book_block", "user_exp_ledger", "level_rule", "realm_catalog",
            "quest_definition", "quest_campaign", "quest_reward", "user_quest_progress",
            "quest_claim", "reading_session", "reading_daily_counter",
            "reading_heartbeat_receipt"}) {
            assertThat(sql)
                .describedAs("migration phải tạo bảng %s", table)
                .contains("CREATE TABLE IF NOT EXISTS `" + table + "`");
        }

        // Rollback đi bằng cờ tính năng, không bằng SQL đảo. Một lệnh DROP lọt vào đây sẽ xoá sổ
        // cái khi migration chạy lại.
        assertThat(sql).doesNotContain("DROP TABLE");
        assertThat(sql).doesNotContain("TRUNCATE");
    }

    @Test
    void ticketBalanceCannotGoNegativeAndLedgerIsImmutable() throws Exception {
        String sql = read(MIGRATION);

        // Ngọn Đuốc không có trạng thái nợ; đây là hàng rào cuối ở tầng lưu trữ.
        assertThat(sql).contains("CONSTRAINT `chk_mt_account_balance` CHECK (`available_balance` >= 0)");
        assertThat(sql).contains("CONSTRAINT `chk_mt_lot_amounts`");

        assertThat(sql).contains("`trg_monthly_ticket_ledger_no_update`");
        assertThat(sql).contains("`trg_monthly_ticket_ledger_no_delete`");
        assertThat(sql).contains("`trg_monthly_ticket_alloc_no_update`");
        assertThat(sql).contains("`trg_user_exp_ledger_no_update`");
        assertThat(sql).contains("`trg_quest_claim_no_update`");
        assertThat(sql).contains("`trg_reading_heartbeat_receipt_no_update`");
        assertThat(sql).contains("`trg_reading_heartbeat_receipt_no_delete`");
        assertThat(sql).contains("`trg_monthly_rank_entry_no_update`");
        assertThat(sql).contains("SIGNAL SQLSTATE '45000'");

        // Kết quả không được đổi sau khi chốt kỳ, và snapshot đã niêm phong là bất biến.
        assertThat(sql).contains("`trg_monthly_ticket_season_no_backward`");
        assertThat(sql).contains("`trg_monthly_rank_snapshot_sealed`");
        assertThat(sql).contains("`trg_monthly_rank_snapshot_no_delete`");
        assertThat(sql).contains("`trg_gamification_event_no_delete`");
        assertThat(sql).contains("`trg_level_rule_no_update`");
        assertThat(sql).contains("`trg_level_rule_no_delete`");
    }

    @Test
    void closedDomainStatesCannotBeReopened() throws Exception {
        String sql = read(MIGRATION);

        assertThat(sql).contains("closed monthly_ticket_lot cannot be reopened or changed");
        assertThat(sql).contains("voided monthly_ticket_vote cannot be restored or changed");
        assertThat(sql).contains("invalid monthly_ticket_season status transition");
        assertThat(sql).contains("sealed monthly_rank_snapshot cannot be reopened");
        assertThat(sql).contains("OLD.`status` = 'FINALIZED' AND NEW.`status` = 'REWARDED'");
        assertThat(sql).doesNotContain(
            "OLD.`status` IN ('FINALIZED', 'REWARDED')\n        AND NEW.`status` IN ('OPEN', 'CLOSING', 'REVIEW')");
    }

    @Test
    void semiImmutableTriggersCompareColumnsWithNullSafeEquality() throws Exception {
        String sql = read(MIGRATION);

        // `NEW.x <> OLD.x` trả về NULL khi một vế là NULL, mệnh đề IF không chạy, và hàng rào bị
        // thủng đúng ở các cột nullable. Toán tử `<=>` là bắt buộc trong mọi trigger bán bất biến.
        assertThat(sql)
            .describedAs("trigger bán bất biến phải dùng toán tử so sánh an toàn với NULL")
            .contains("NOT (NEW.`user_id` <=> OLD.`user_id`)")
            .contains("NOT (NEW.`author_id` <=> OLD.`author_id`)")
            .contains("NOT (NEW.`book_id` <=> OLD.`book_id`)");

        for (String forbidden : new String[]{
            "NEW.`user_id` <> OLD.`user_id`",
            "NEW.`author_id` <> OLD.`author_id`",
            "NEW.`book_id` <> OLD.`book_id`",
            "NEW.`grant_ledger_id` <> OLD.`grant_ledger_id`"}) {
            assertThat(sql)
                .describedAs("so sánh không an toàn NULL trong trigger: %s", forbidden)
                .doesNotContain(forbidden);
        }
    }

    @Test
    void idempotencyAndAntiAbuseKeysAreDeclared() throws Exception {
        String sql = read(MIGRATION);

        // Trọng tài cuối của idempotency; tầng service dựa vào DuplicateKeyException từ khoá này.
        assertThat(sql).contains("UNIQUE KEY `uk_mt_ledger_idempotency` (`idempotency_key`)");
        assertThat(sql).contains("UNIQUE KEY `uk_mt_vote_user_client` (`user_id`, `client_request_id`)");
        assertThat(sql).contains("UNIQUE KEY `uk_mt_lot_grant_ledger` (`grant_ledger_id`)");
        assertThat(sql).contains("UNIQUE KEY `uk_mt_alloc_ledger_lot` (`ledger_id`, `lot_id`)");
        assertThat(sql).contains("UNIQUE KEY `uk_gamification_event_source` (`source_key`)");
        assertThat(sql).contains("UNIQUE KEY `uk_quest_claim_scope` (`user_id`, `quest_code`, `period_key`)");
        assertThat(sql).contains(
            "UNIQUE KEY `uk_reading_heartbeat_sequence` (`session_id`, `sequence_no`)");
        assertThat(sql).contains("UNIQUE KEY `uk_job_run_scope` (`job_type`, `scope_type`, `scope_key`)");
        assertThat(sql).contains("UNIQUE KEY `uk_reward_allocation_season_book` (`season_id`, `book_id`)");

        // Chỉ mục phục vụ đúng thứ tự tiêu FIFO; thiếu nó thì truy vấn sẽ filesort trong lúc đang
        // giữ khoá FOR UPDATE.
        assertThat(sql).contains("KEY `idx_mt_lot_fifo` (`user_id`, `status`, `expire_at`, `id`)");

        // Trần theo ngày phải nằm ở database vì bộ giới hạn dựa trên Redis tụt về bộ đếm theo
        // từng instance khi Redis ngừng hoạt động.
        assertThat(sql).contains("CREATE TABLE IF NOT EXISTS `monthly_ticket_daily_counter`");
        assertThat(sql).contains("PRIMARY KEY (`user_id`, `local_date`)");
    }

    @Test
    void rewardFlowsThroughClearingAndKeepsFourEyesAccountability() throws Exception {
        String sql = read(MIGRATION);

        // Thưởng đi qua ví clearing của hệ thống nên bút toán thu hồi chỉ chạm hai ví SYSTEM và
        // không bao giờ đẩy ví tác giả xuống âm.
        assertThat(sql).contains("'REWARD_CLEARING'");
        assertThat(sql).contains("INSERT IGNORE INTO `wallet_account`");

        assertThat(sql).contains("`chk_reward_campaign_approved`");
        assertThat(sql).contains("`chk_mt_ledger_operator`");
    }

    @Test
    void migrationIsPackagedInTheFlywayImageAndAppsWaitForIt() throws Exception {
        String compose = read("compose.yaml");
        String flywayImage = read("deploy/flyway/Dockerfile");

        assertThat(flywayImage)
            .describedAs("migration phải có version duy nhất trong image Flyway")
            .contains("COPY --chmod=0444 doc/sql/20260729_gamification_monthly_ticket.sql "
                + "/flyway/sql/V2026072901__gamification_monthly_ticket.sql");
        assertThat(compose)
            .contains("dockerfile: deploy/flyway/Dockerfile")
            .contains("migrate:", "condition: service_completed_successfully");
    }

    @Test
    void mapperKeepsLockingAndAvoidsDatabaseLocalTimeForBusinessValues() throws Exception {
        String mapper = read("novel-common/src/main/resources/mybatis/mapping/MonthlyTicketMapper.xml");
        String rankingMapper = read(
            "novel-common/src/main/resources/mybatis/mapping/MonthlyRankingMapper.xml");

        assertThat(mapper).contains("FOR UPDATE");
        assertThat(mapper).contains("version = #{expectedVersion}");
        // Thứ tự tiêu FIFO: lô sắp tắt trước, hoà thì lô cũ hơn trước.
        assertThat(mapper).contains("ORDER BY expire_at ASC, id ASC");

        // MySQL chạy múi giờ +07:00 còn JVM có thể chạy UTC. Giá trị nghiệp vụ phụ thuộc múi giờ
        // phải tính ở Java và truyền xuống làm tham số. So sánh dấu thời gian do chính database
        // ghi ra thì vẫn hợp lệ, nên chỉ cấm hàm trả về ngày theo giờ máy chủ database.
        String statements = withoutXmlComments(mapper);
        assertThat(statements).doesNotContain("CURDATE()");
        assertThat(statements).doesNotContain("CURRENT_DATE");

        String rankingStatements = withoutXmlComments(rankingMapper);
        assertThat(rankingStatements)
            .contains("FROM monthly_ticket_vote vote")
            .contains("aggregate.total_tickets DESC")
            .contains("aggregate.distinct_voter_count DESC")
            .contains("aggregate.last_vote_at ASC")
            .contains("aggregate.book_id ASC")
            .contains("advanceJobCheckpoint")
            .contains("status = 'SEALED'")
            .doesNotContain("CURDATE()")
            .doesNotContain("CURRENT_DATE");
    }

    @Test
    void voteApiKeepsIdentityServerSideAndDatabaseQuotasAuthoritative() throws Exception {
        String controller = read(
            "novel-front/src/main/java/com/java2nb/novel/controller/MonthlyTicketController.java");
        String request = read(
            "novel-front/src/main/java/com/java2nb/novel/dto/gamification/MonthlyTicketVoteRequest.java");
        String mapper = read("novel-common/src/main/resources/mybatis/mapping/MonthlyTicketMapper.xml");

        assertThat(controller)
            .contains("requireUser(request).getId()")
            .contains("@RateLimit")
            .contains("hashIdentifier(\"IP\", IpUtil.getRealIp(request))");
        assertThat(request).doesNotContain("userId", "authorId");
        assertThat(mapper)
            .contains("tryConsumeDailyQuota")
            .contains("tryConsumeBookQuota")
            .contains("lockSpendableLots")
            .contains("ORDER BY expire_at ASC, id ASC")
            .contains("FOR UPDATE")
            .contains("countSeasonStillOpen")
            .contains("WHEN last_vote_at IS NULL OR last_vote_at &lt; VALUES(last_vote_at)");
    }

    @Test
    void authorRewardHistoryIsReachableAndSurvivesThemeOverlays() throws Exception {
        String page = read("novel-front/src/main/resources/templates/author/monthly_rewards.html");
        String pageController = read(
            "novel-front/src/main/java/com/java2nb/novel/controller/page/PageController.java");
        String mapper = read("novel-common/src/main/resources/mybatis/mapping/AuthorRewardMapper.xml");

        assertThat(page)
            .contains("/author/monthly-rewards", "reward.periodCode", "reward.bookName")
            .contains("textContent", "replaceChildren")
            .doesNotContain("innerHTML");
        assertThat(pageController)
            .contains("@RequestMapping(\"author/monthly_rewards.html\")")
            .contains("return \"author/monthly_rewards\"");
        assertThat(mapper)
            .contains("season.period_code AS periodCode")
            .contains("book.book_name AS bookName")
            .contains("WHERE allocation.author_id = #{authorId}");

        for (String incomePage : new String[]{
            "novel-front/src/main/resources/templates/author/author_income.html",
            "templates/green/html/author/author_income.html",
            "templates/orange/html/author/author_income.html"}) {
            assertThat(read(incomePage)).contains("/author/monthly_rewards.html");
        }
    }

    @Test
    void readerQuestPageUsesSharedAssetsAndSurvivesThemeOverlays() throws Exception {
        String desktop = read("novel-front/src/main/resources/templates/user/quests.html");
        String mobile = read("novel-front/src/main/resources/templates/mobile/user/quests.html");
        String pageController = read(
            "novel-front/src/main/java/com/java2nb/novel/controller/page/PageController.java");
        String api = read("novel-front/src/main/resources/static/javascript/gamification-api.js");
        String ui = read("novel-front/src/main/resources/static/javascript/gamification.js");
        String css = read("novel-front/src/main/resources/static/css/gamification.css");

        assertThat(pageController)
            .contains("@RequestMapping(\"user/quests.html\")")
            .contains("ThreadLocalUtil.getTemplateDir() + \"user/quests\"");
        for (String page : new String[]{desktop, mobile}) {
            assertThat(page)
                .contains("id=\"gamificationApp\"")
                .contains("id=\"gamificationCheckIn\"")
                .contains("id=\"checkInButton\"")
                .contains("th:data-check-in=\"#{gamification.checkIn.action}\"")
                .contains("/javascript/gamification-api.js")
                .contains("/javascript/gamification.js")
                .contains("/css/gamification.css")
                .contains("aria-live=\"polite\"");
        }
        assertThat(api)
            .contains("credentials = 'same-origin'")
            .contains("/user/gamification/profile")
            .contains("/user/gamification/quests")
            .contains("/user/gamification/check-in");
        assertThat(ui)
            .contains("textContent", "replaceChildren", "createElement('button')", "api.checkIn()")
            .doesNotContain("innerHTML");
        assertThat(css)
            .contains(":focus-visible")
            .contains("prefers-reduced-motion")
            .contains("var(--accent-emerald")
            .contains(".gamification-check-in");

        for (String userPage : new String[]{
            "novel-front/src/main/resources/templates/user/userinfo.html",
            "novel-front/src/main/resources/templates/mobile/user/userinfo.html",
            "templates/green/html/user/userinfo.html",
            "templates/orange/html/user/userinfo.html"}) {
            assertThat(read(userPage)).contains("/user/quests.html");
        }
    }

    @Test
    void administrationMenuAndSeparatedPermissionsAreSeeded() throws Exception {
        String sql = read(MIGRATION);

        for (String permission : new String[]{
            "novel:gamification:view", "novel:gamification:config", "novel:gamification:review",
            "novel:gamification:grant", "novel:gamification:finalize", "novel:gamification:reward",
            "novel:gamification:adjust"}) {
            assertThat(sql).contains("'" + permission + "'");
        }

        // Seed phải chạy lại được mà không nhân đôi menu và không đè tuỳ chỉnh của quản trị viên.
        assertThat(sql).contains("WHERE NOT EXISTS");
        assertThat(sql).contains("INSERT INTO `sys_role_menu`");
    }

    @Test
    void policyDefaultsStayDisabledInTheShippedConfiguration() throws Exception {
        String applicationYml = read("novel-front/src/main/resources/application.yml");

        for (String flag : new String[]{
            "GAMIFICATION_EVENT_ENABLED:false", "GAMIFICATION_TICKET_ENABLED:false",
            "GAMIFICATION_VOTE_ENABLED:false", "GAMIFICATION_QUEST_ENABLED:false",
            "GAMIFICATION_REALM_ENABLED:false", "GAMIFICATION_SEASON_ENABLED:false",
            "GAMIFICATION_REWARD_ENABLED:false", "GAMIFICATION_SEASON_AUTO_FINALIZE:false",
            "GAMIFICATION_TICKET_GRANT_ON_TOPUP:false", "GAMIFICATION_VOTE_ALLOW_CRAWLED:false"}) {
            assertThat(applicationYml)
                .describedAs("cờ %s phải mặc định tắt", flag)
                .contains("${" + flag + "}");
        }
    }

    @Test
    void composePassesGamificationConfigurationIntoApplications() throws Exception {
        String compose = read("compose.yaml");
        String adminYml = read("novel-admin/src/main/resources/application.yml");

        for (String variable : new String[]{
            "GAMIFICATION_POLICY_VERSION", "GAMIFICATION_ZONE_ID",
            "GAMIFICATION_TICKET_ENABLED", "GAMIFICATION_TICKET_LOT_DAYS",
            "GAMIFICATION_TICKET_MAX_GRANT_BATCH", "GAMIFICATION_VOTE_ENABLED",
            "GAMIFICATION_VOTE_IP_HASH_SALT", "GAMIFICATION_JOB_LEASE_SECONDS"}) {
            assertThat(compose)
                .describedAs("compose phải truyền biến %s vào container ứng dụng", variable)
                .contains(variable + ": ${" + variable + ":-");
        }
        assertThat(adminYml)
            .contains("${GAMIFICATION_TICKET_LOT_DAYS:60}")
            .contains("${GAMIFICATION_TICKET_MAX_GRANT_BATCH:1000}");
    }

    @Test
    void mysqlAcceptanceWorkflowIsReachableFromTheHost() throws Exception {
        String compose = read("compose.yaml");
        String testCompose = read("compose.test.yaml");
        String script = read("scripts/verify-gamification.ps1");
        String workflow = read(".github/workflows/gamification-mysql.yml");
        String releaseWorkflow = read(".github/workflows/release.yml");

        assertThat(compose).doesNotContain("127.0.0.1:${MYSQL_HOST_PORT:-3307}:3306");
        assertThat(testCompose).contains("127.0.0.1:${MYSQL_HOST_PORT:-3307}:3306");
        assertThat(script)
            .contains("'compose.test.yaml'")
            .contains("$composeArguments + @('run', '--rm', 'migrate')")
            .contains("GamificationMySqlIntegrationTest,MonthlyTicketConcurrencyIT")
            .contains("MonthlySeasonConcurrencyIT")
            .contains("-Dgamification.mysql.it=true")
            .contains("-Dgamification.concurrency.it=true")
            .contains("$env:SPRING_DATASOURCE_URL = $jdbcUrl")
            .contains("$env:SPRING_DATASOURCE_PASSWORD = $password")
            .doesNotContain("-Dspring.datasource.password");
        assertThat(workflow)
            .contains("actions/checkout@v4")
            .contains("actions/setup-java@v4")
            .contains("./scripts/verify-gamification.ps1 -StopAfter")
            .contains("timeout-minutes: 45");
        assertThat(releaseWorkflow)
            .contains("./scripts/verify-gamification.ps1 -StopAfter")
            .contains("mvn -B -ntp clean verify -Pcentral-repo")
            .doesNotContain("-DskipTests");
    }
}
