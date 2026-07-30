package com.java2nb.novel.service.gamification;

import com.java2nb.novel.FrontNovelApplication;
import com.java2nb.novel.core.config.GamificationProperties;
import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.mapper.MonthlyTicketMapper;
import com.java2nb.novel.service.impl.GamificationEventFailureWriter;
import com.java2nb.novel.service.impl.GamificationEventProcessor;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest(
    classes = FrontNovelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.task.scheduling.enabled=false",
        "management.health.mail.enabled=false"
    }
)
@EnabledIfSystemProperty(named = "gamification.mysql.it", matches = "true")
@Transactional
@Rollback
class GamificationMySqlIntegrationTest {

    private static final long USER_ID = 99_741_001L;
    private static final long AUTHOR_ID = 99_741_101L;
    private static final long BOOK_ID = 99_741_201L;

    @Autowired
    private MonthlyTicketService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private MonthlyTicketMapper monthlyTicketMapper;

    @Autowired
    private AuthorRewardService authorRewardService;

    @Autowired
    private GamificationProgressService gamificationProgressService;

    @Autowired
    private QuestCampaignConfigService questCampaignConfigService;

    @Autowired
    private GamificationEventProcessor gamificationEventProcessor;

    @Autowired
    private GamificationProgressMapper gamificationProgressMapper;

    @Autowired
    private GamificationEventFailureWriter gamificationEventFailureWriter;

    @Autowired
    private GamificationProperties gamificationProperties;

    @Autowired
    private ReadingHeartbeatWriter readingHeartbeatWriter;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void eventProcessorAdvancesQuestOnceAndSkipsUnmatchedEvents() {
        long suffix = Math.floorMod(System.nanoTime(), 900_000);
        long userId = 99_743_000_000L + suffix;
        String paidSource = "MYSQL_IT:QUEST:PAID:" + userId;
        String topUpSource = "MYSQL_IT:QUEST:TOP_UP:" + userId;
        LocalDate localDate = LocalDate.of(2027, 1, 1);
        Date occurredAt = Date.from(java.time.Instant.parse("2027-01-01T03:00:00Z"));
        deleteQuestProgressFixture(userId);
        try {
            jdbcTemplate.update("""
                INSERT INTO gamification_event
                    (event_type, source_key, user_id, book_id, occurred_at, local_date,
                     payload_hash, status, attempt, policy_version)
                VALUES ('CHAPTER_PURCHASED', ?, ?, ?, ?, ?, ?, 'PENDING', 0, 'v1')
                """, paidSource, userId, BOOK_ID, occurredAt, localDate, "a".repeat(64));
            Long paidEventId = jdbcTemplate.queryForObject(
                "SELECT id FROM gamification_event WHERE source_key=?", Long.class, paidSource);

            assertThat(gamificationEventProcessor.process(paidEventId, occurredAt, 10))
                .isEqualTo(EventProcessResult.PROCESSED);
            assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM gamification_event WHERE id=?", String.class, paidEventId))
                .isEqualTo("PROCESSED");
            assertThat(jdbcTemplate.queryForObject("""
                SELECT current_count FROM user_quest_progress
                WHERE user_id=? AND quest_code='DAILY_PAID_CHAPTER' AND period_key='2027-01-01'
                """, Integer.class, userId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                SELECT completed_at IS NOT NULL FROM user_quest_progress
                WHERE user_id=? AND quest_code='DAILY_PAID_CHAPTER' AND period_key='2027-01-01'
                """, Boolean.class, userId)).isTrue();

            assertThat(gamificationEventProcessor.process(paidEventId, occurredAt, 10))
                .isEqualTo(EventProcessResult.NOT_OWNER);
            assertThat(jdbcTemplate.queryForObject("""
                SELECT current_count FROM user_quest_progress
                WHERE user_id=? AND quest_code='DAILY_PAID_CHAPTER' AND period_key='2027-01-01'
                """, Integer.class, userId)).isEqualTo(1);

            jdbcTemplate.update("""
                INSERT INTO gamification_event
                    (event_type, source_key, user_id, occurred_at, local_date,
                     payload_hash, status, attempt, policy_version)
                VALUES ('TOP_UP_SETTLED', ?, ?, ?, ?, ?, 'PENDING', 0, 'v1')
                """, topUpSource, userId, occurredAt, localDate, "b".repeat(64));
            Long topUpEventId = jdbcTemplate.queryForObject(
                "SELECT id FROM gamification_event WHERE source_key=?", Long.class, topUpSource);

            assertThat(gamificationEventProcessor.process(topUpEventId, occurredAt, 10))
                .isEqualTo(EventProcessResult.SKIPPED);
            assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM gamification_event WHERE id=?", String.class, topUpEventId))
                .isEqualTo("SKIPPED");
        } finally {
            deleteQuestProgressFixture(userId);
        }
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void questClaimPostsImmutableRewardsOnceAndAdvancesLevel() {
        long suffix = Math.floorMod(System.nanoTime(), 900_000);
        long userId = 99_744_000_000L + suffix;
        LocalDate localDate = LocalDate.of(2027, 1, 1);
        Date claimedAt = Date.from(java.time.Instant.parse("2027-01-01T03:00:00Z"));
        String seedSource = "MYSQL_IT:EXP_SEED:" + userId;
        jdbcTemplate.update("""
            INSERT INTO gamification_profile (user_id, level, total_exp, rule_version)
            VALUES (?, 1, 90, 'v1')
            """, userId);
        jdbcTemplate.update("""
            INSERT INTO user_exp_ledger
                (user_id, source_key, source_type, amount, balance_after, rule_version, policy_version)
            VALUES (?, ?, 'TEST_SEED', 90, 90, 'v1', 'v1')
            """, userId, seedSource);
        jdbcTemplate.update("""
            INSERT INTO user_quest_progress
                (user_id, quest_code, period_key, current_count, target_count, completed_at)
            VALUES (?, 'DAILY_READING', '2027-01-01', 30, 30, ?)
            """, userId, claimedAt);
        QuestClaimCommand command = new QuestClaimCommand(userId, "DAILY_READING", localDate,
            claimedAt, ZoneId.of("Asia/Ho_Chi_Minh"), 60, "v1", "v1");

        QuestClaimResult first = gamificationProgressService.claimQuest(command);
        QuestClaimResult replay = gamificationProgressService.claimQuest(command);

        assertThat(first.alreadyClaimed()).isFalse();
        assertThat(replay.alreadyClaimed()).isTrue();
        assertThat(first.claim().getExpAmount()).isEqualTo(20L);
        assertThat(first.claim().getTicketAmount()).isEqualTo(1L);
        assertThat(first.profile().profile().getTotalExp()).isEqualTo(110L);
        assertThat(first.profile().profile().getLevel()).isEqualTo(2);
        assertThat(first.ticketBalance()).isEqualTo(1L);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM quest_claim
            WHERE user_id=? AND quest_code='DAILY_READING' AND period_key='2027-01-01'
            """, Integer.class, userId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM user_exp_ledger WHERE source_key=?", Integer.class,
            "QUEST_EXP:" + userId + ":DAILY_READING:2027-01-01")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM monthly_ticket_ledger WHERE idempotency_key=?", Integer.class,
            "QUEST_TICKET:" + userId + ":DAILY_READING:2027-01-01")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gamification_event WHERE source_key=?", Integer.class,
            "GAMIFY:LEVEL_REACHED:" + userId + ":2:v1")).isEqualTo(1);

        Long expLedgerId = first.claim().getExpLedgerId();
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE user_exp_ledger SET amount=21 WHERE id=?", expLedgerId))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("user_exp_ledger is immutable");
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE quest_claim SET exp_amount=21 WHERE id=?", first.claim().getId()))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("quest_claim is immutable");
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void checkInProcessesAndClaimsDailyRewardExactlyOnce() {
        long suffix = Math.floorMod(System.nanoTime(), 900_000);
        long userId = 99_745_000_000L + suffix;
        Instant now = Instant.parse("2027-01-01T03:00:00Z");
        Date checkedAt = Date.from(now);
        LocalDate localDate = LocalDate.of(2027, 1, 1);
        String sourceKey = "CHECKIN:" + userId + ":2027-01-01";
        GamificationCheckInService checkInService = new GamificationCheckInService(
            gamificationProgressService, gamificationProgressMapper, gamificationEventProcessor,
            gamificationEventFailureWriter, gamificationProperties,
            Clock.fixed(now, ZoneOffset.UTC));

        CheckInOutcome first = checkInService.checkIn(userId);
        CheckInOutcome replay = checkInService.checkIn(userId);

        assertThat(first.checkIn().alreadyCheckedIn()).isFalse();
        assertThat(first.checkIn().profile().getCheckinStreak()).isEqualTo(1);
        assertThat(first.checkIn().profile().getLongestStreak()).isEqualTo(1);
        assertThat(first.reward().claim().getExpAmount()).isEqualTo(10L);
        assertThat(first.reward().claim().getTicketAmount()).isZero();
        assertThat(first.reward().profile().profile().getTotalExp()).isEqualTo(10L);
        assertThat(first.nextCheckIn())
            .isEqualTo(Date.from(Instant.parse("2027-01-01T17:00:00Z")));
        assertThat(replay.checkIn().alreadyCheckedIn()).isTrue();
        assertThat(replay.reward().alreadyClaimed()).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM gamification_event
            WHERE source_key=? AND event_type='CHECK_IN_COMPLETED' AND status='PROCESSED'
            """, Integer.class, sourceKey)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT checkin_streak FROM gamification_profile WHERE user_id=?
            """, Integer.class, userId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT current_count FROM user_quest_progress
            WHERE user_id=? AND quest_code='DAILY_CHECK_IN' AND period_key=?
            """, Integer.class, userId, localDate.toString())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM quest_claim
            WHERE user_id=? AND quest_code='DAILY_CHECK_IN' AND period_key=?
            """, Integer.class, userId, localDate.toString())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM user_exp_ledger
            WHERE user_id=? AND source_key=? AND amount=10
            """, Integer.class, userId,
            "QUEST_EXP:" + userId + ":DAILY_CHECK_IN:" + localDate)).isEqualTo(1);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void readingHeartbeatCreditsAndProjectsEachMinuteExactlyOnce() {
        long suffix = Math.floorMod(System.nanoTime(), 800_000);
        long userId = 99_746_000_000L + suffix;
        long authorId = 99_747_000_000L + suffix;
        long bookId = 99_748_000_000L + suffix;
        long chapterId = 99_749_000_000L + suffix;
        String sessionId = java.util.UUID.randomUUID().toString().replace("-", "");
        Instant startedAt = Instant.parse("2027-01-01T03:00:00Z");
        seedReadableChapter(userId, authorId, bookId, chapterId, suffix);
        ReadingHeartbeatInput start = new ReadingHeartbeatInput(
            sessionId, bookId, chapterId, 0, 0);
        ReadingHeartbeatInput minute = new ReadingHeartbeatInput(
            sessionId, bookId, chapterId, 1, 60);

        ReadingHeartbeatResult opened = heartbeatService(startedAt).record(userId, start);
        ReadingHeartbeatResult credited = heartbeatService(startedAt.plusSeconds(60))
            .record(userId, minute);
        ReadingHeartbeatResult replay = heartbeatService(startedAt.plusSeconds(90))
            .record(userId, minute);

        assertThat(opened.acceptedSeconds()).isZero();
        assertThat(credited.acceptedSeconds()).isEqualTo(60);
        assertThat(credited.verifiedMinutesToday()).isEqualTo(1);
        assertThat(replay.replayed()).isTrue();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT verified_seconds FROM reading_daily_counter
            WHERE user_id=? AND local_date='2027-01-01'
            """, Integer.class, userId)).isEqualTo(60);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM reading_heartbeat_receipt WHERE session_id=?
            """, Integer.class, sessionId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM gamification_event
            WHERE source_key=? AND status='PROCESSED'
            """, Integer.class,
            "READ:" + userId + ':' + sessionId + ":2027-01-01:1")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT current_count FROM user_quest_progress
            WHERE user_id=? AND quest_code='DAILY_READING' AND period_key='2027-01-01'
            """, Integer.class, userId)).isEqualTo(1);
        Long receiptId = jdbcTemplate.queryForObject("""
            SELECT id FROM reading_heartbeat_receipt
            WHERE session_id=? AND sequence_no=1
            """, Long.class, sessionId);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE reading_heartbeat_receipt SET accepted_seconds=59 WHERE id=?", receiptId))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("reading_heartbeat_receipt is immutable");
    }

    @Test
    void profileRealmTransitionUsesVersionCooldownLevelAndAudit() {
        long userId = 99_742_001L;
        Date firstChange = Date.from(java.time.Instant.parse("2026-07-28T03:00:00Z"));
        GamificationProfileRow initial = gamificationProgressService.getProfile(userId, "v1");
        assertThat(initial.getLevel()).isEqualTo(1);
        assertThat(initial.getVersion()).isZero();

        RealmUpdateResult first = gamificationProgressService.updateRealm(userId, "NHAP_MON", 0L,
            firstChange, ZoneId.of("Asia/Ho_Chi_Minh"), 24, "v1");
        assertThat(first.profile().getRealmCode()).isEqualTo("NHAP_MON");
        assertThat(first.profile().getVersion()).isEqualTo(1L);

        RealmUpdateResult retry = gamificationProgressService.updateRealm(userId, "NHAP_MON", 0L,
            firstChange, ZoneId.of("Asia/Ho_Chi_Minh"), 24, "v1");
        assertThat(retry.profile().getVersion()).isEqualTo(1L);

        assertThatThrownBy(() -> gamificationProgressService.updateRealm(userId, "TIEN_PHONG", 1L,
            Date.from(java.time.Instant.parse("2026-07-30T03:00:00Z")),
            ZoneId.of("Asia/Ho_Chi_Minh"), 24, "v1"))
            .isInstanceOf(com.java2nb.novel.core.exception.BusinessException.class);

        jdbcTemplate.update("UPDATE gamification_profile SET level=3 WHERE user_id=?", userId);
        RealmUpdateResult promoted = gamificationProgressService.updateRealm(userId, "TIEN_PHONG", 1L,
            Date.from(java.time.Instant.parse("2026-07-30T03:00:00Z")),
            ZoneId.of("Asia/Ho_Chi_Minh"), 24, "v1");
        assertThat(promoted.profile().getRealmCode()).isEqualTo("TIEN_PHONG");
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM gamification_profile_audit WHERE user_id=? AND change_type='REALM'",
            Integer.class, userId)).isEqualTo(2);
    }

    @Test
    void questCampaignWindowUsesInclusiveStartExclusiveEndAndRejectsOverlap() {
        Date startAt = Date.from(Instant.parse("2098-01-01T00:00:00Z"));
        Date endAt = Date.from(Instant.parse("2098-02-01T00:00:00Z"));
        jdbcTemplate.update("""
            INSERT INTO quest_campaign
                (campaign_code, start_at, end_at, status, policy_version)
            VALUES ('MYSQL_IT_CAMPAIGN', ?, ?, 'ACTIVE', 'v2')
            """, startAt, endAt);

        assertThat(gamificationProgressMapper.selectActiveQuestCampaigns(startAt))
            .extracting(QuestCampaignRow::getCampaignCode)
            .containsExactly("MYSQL_IT_CAMPAIGN");
        assertThat(gamificationProgressMapper.selectActiveQuestCampaigns(endAt)).isEmpty();

        Date overlapStartAt = Date.from(Instant.parse("2098-01-15T00:00:00Z"));
        Date overlapObservedAt = Date.from(Instant.parse("2098-01-16T00:00:00Z"));
        jdbcTemplate.update("""
            INSERT INTO quest_campaign
                (campaign_code, start_at, end_at, status, policy_version)
            VALUES ('MYSQL_IT_CAMPAIGN_OVERLAP', ?, ?, 'ACTIVE', 'v2')
            """, overlapStartAt, endAt);

        assertThatThrownBy(() -> gamificationProgressService.listQuests(
            USER_ID, LocalDate.of(2098, 1, 16), overlapObservedAt))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("chồng lấn");
    }

    @Test
    void questCampaignAdminLifecyclePersistsRewardsAndPreventsOverlap() {
        Date firstStart = Date.from(Instant.parse("2097-01-01T00:00:00Z"));
        Date firstEnd = Date.from(Instant.parse("2097-02-01T00:00:00Z"));
        Date secondStart = Date.from(Instant.parse("2097-01-15T00:00:00Z"));
        Date secondEnd = Date.from(Instant.parse("2097-02-15T00:00:00Z"));
        Date activatedAt = Date.from(Instant.parse("2096-12-01T00:00:00Z"));

        QuestCampaignRow first = questCampaignConfigService.createDraft(
            new QuestCampaignDraftCommand("MYSQL_IT_CONFIG_A", firstStart, firstEnd, "v1"));
        questCampaignConfigService.replaceReward(first.getId(),
            new QuestRewardCommand("DAILY_READING", 25L, 2L));
        assertThat(questCampaignConfigService.activate(first.getId(), activatedAt).getStatus())
            .isEqualTo("ACTIVE");

        QuestCampaignRow second = questCampaignConfigService.createDraft(
            new QuestCampaignDraftCommand("MYSQL_IT_CONFIG_B", secondStart, secondEnd, "v1"));
        questCampaignConfigService.replaceReward(second.getId(),
            new QuestRewardCommand("DAILY_READING", 30L, 3L));
        assertThatThrownBy(() -> questCampaignConfigService.activate(second.getId(), activatedAt))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("chồng lấn");

        assertThat(questCampaignConfigService.close(first.getId()).getStatus()).isEqualTo("CLOSED");
        assertThat(questCampaignConfigService.activate(second.getId(), activatedAt).getStatus())
            .isEqualTo("ACTIVE");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM quest_reward
            WHERE campaign_code='MYSQL_IT_CONFIG_B' AND quest_code='DAILY_READING'
            """, Integer.class)).isEqualTo(2);
    }

    @Test
    void grantVoteAndStorageTriggersPreserveInvariants() {
        Date now = new Date();
        seedFixture(now);
        TicketPolicy policy = new TicketPolicy("v1", 60, 10, 20, 50, 100, 50, false);

        assertThat(service.grant(new TicketGrantCommand(USER_ID, 5, "ADMIN_GRANT", "mysql-it",
            "ADMIN_GRANT:mysql-it:" + USER_ID, new Date(now.getTime() - 60_000),
            new Date(now.getTime() + 86_400_000), "ADMIN", 1L, "integration test", "v1")))
            .isEqualTo(TicketPostResult.POSTED);

        TicketVoteResult result = service.castVote(new TicketVoteCommand(USER_ID, BOOK_ID, 3,
            "mysql-it-request-1", "b".repeat(64), now, LocalDate.of(2026, 8, 15)), policy);

        assertThat(result.status()).isEqualTo(TicketPostResult.POSTED);
        assertThat(result.availableBalance()).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT available_balance FROM monthly_ticket_account WHERE user_id=?",
            Long.class, USER_ID)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT SUM(a.amount)
            FROM monthly_ticket_lot_allocation a
            JOIN monthly_ticket_ledger l ON l.id = a.ledger_id
            WHERE l.user_id=? AND l.entry_type='SPEND'
            """, Long.class, USER_ID)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM monthly_ticket_vote WHERE user_id=? AND book_id=?",
            Integer.class, USER_ID, BOOK_ID)).isEqualTo(1);

        Long ledgerId = jdbcTemplate.queryForObject(
            "SELECT id FROM monthly_ticket_ledger WHERE user_id=? ORDER BY id DESC LIMIT 1",
            Long.class, USER_ID);
        assertThatThrownBy(() -> jdbcTemplate.update(
            "UPDATE monthly_ticket_ledger SET amount=1 WHERE id=?", ledgerId))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("monthly_ticket_ledger is immutable");
        assertThatThrownBy(() -> jdbcTemplate.update("""
            UPDATE monthly_ticket_season SET status='REWARDED'
            WHERE period_code='2099-10' AND status='OPEN'
            """))
            .isInstanceOf(DataAccessException.class)
            .hasMessageContaining("invalid monthly_ticket_season status transition");
    }

    @Test
    void expiryClosesLotsAndJobClaimCanBeTakenOverSafely() {
        Date now = new Date();
        seedFixture(now);
        Date effectiveAt = new Date(now.getTime() - 172_800_000L);
        Date expireAt = new Date(now.getTime() - 86_400_000L);

        assertThat(service.grant(new TicketGrantCommand(USER_ID, 5, "ADMIN_GRANT", "expiry-it",
            "ADMIN_GRANT:expiry-it:" + USER_ID, effectiveAt, expireAt,
            "ADMIN", 1L, "integration expiry test", "v1")))
            .isEqualTo(TicketPostResult.POSTED);

        TicketExpiryResult expiry = service.expireDueLots(
            USER_ID, now, "2026-08-15", "v1");

        assertThat(expiry).isEqualTo(new TicketExpiryResult(1, 5));
        assertThat(jdbcTemplate.queryForObject(
            "SELECT available_balance FROM monthly_ticket_account WHERE user_id=?",
            Long.class, USER_ID)).isZero();
        assertThat(jdbcTemplate.queryForObject(
            "SELECT status FROM monthly_ticket_lot WHERE user_id=?",
            String.class, USER_ID)).isEqualTo("EXPIRED");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM monthly_ticket_ledger
            WHERE user_id=? AND entry_type='EXPIRE' AND amount=-5
            """, Integer.class, USER_ID)).isEqualTo(1);

        Date claimedAt = new Date(now.getTime() + 1_000L);
        assertThat(monthlyTicketMapper.insertJobRunIgnore(
            "LOT_EXPIRY", "DATE", "2026-08-15", "mysql-it-owner-1", now)).isEqualTo(1);
        assertThat(monthlyTicketMapper.insertJobRunIgnore(
            "LOT_EXPIRY", "DATE", "2026-08-15", "mysql-it-owner-2", now)).isZero();
        assertThat(monthlyTicketMapper.claimStaleJobRun(
            "LOT_EXPIRY", "DATE", "2026-08-15", "mysql-it-owner-2", claimedAt, claimedAt))
            .isEqualTo(1);
        assertThat(monthlyTicketMapper.advanceJobCheckpoint(
            "LOT_EXPIRY", "DATE", "2026-08-15", "mysql-it-owner-2",
            Long.toString(USER_ID), 1, claimedAt)).isEqualTo(1);
        assertThat(monthlyTicketMapper.completeJobRun(
            "LOT_EXPIRY", "DATE", "2026-08-15", "mysql-it-owner-2", claimedAt)).isEqualTo(1);
        assertThat(monthlyTicketMapper.checkStuckJobs(
            Date.from(now.toInstant().minusSeconds(300)),
            Date.from(now.toInstant().minusSeconds(72 * 3600L)))).isEmpty();
        assertThat(monthlyTicketMapper.checkPendingRewards(
            Date.from(now.toInstant().minusSeconds(7 * 86400L)))).isEmpty();
    }

    @Test
    void authorRewardPendingAndClawbackStayZeroSumInsideSystemClearing() {
        long seasonId = 99_799_001L;
        long snapshotId = 99_799_101L;
        long bookId = BOOK_ID;
        long authorId = AUTHOR_ID;
        Date now = new Date();
        seedFixture(now);
        jdbcTemplate.update("""
            INSERT INTO monthly_ticket_season
                (id, period_code, zone_id, start_at, end_at, vote_cutoff_at, status,
                 policy_version, snapshot_id, finalized_at, finalized_by)
            VALUES (?, '2199-01', 'Asia/Ho_Chi_Minh', '2199-01-01 00:00:00.000',
                    '2199-02-01 00:00:00.000', '2199-02-01 00:00:00.000',
                    'FINALIZED', 'v1', ?, ?, 8)
            """, seasonId, snapshotId, now);
        jdbcTemplate.update("""
            INSERT INTO monthly_rank_snapshot
                (id, season_id, sequence_no, status, cutoff_at, entry_count,
                 total_tickets, content_hash, sealed_at)
            VALUES (?, ?, 1, 'SEALED', '2199-02-01 00:00:00.000', 1, 10, ?, ?)
            """, snapshotId, seasonId, "f".repeat(64), now);
        jdbcTemplate.update("""
            INSERT INTO monthly_rank_entry
                (snapshot_id, rank_no, book_id, author_id, total_tickets,
                 distinct_voter_count, last_vote_at)
            VALUES (?, 1, ?, ?, 10, 2, '2199-01-20 00:00:00.000')
            """, snapshotId, bookId, authorId);

        RewardCampaignRow campaign = authorRewardService.calculateAllocations(
            new RewardCampaignCommand(seasonId, 101L,
                List.of(new RewardShareRule(1, 10_000)), "v1"));
        authorRewardService.approveCampaign(campaign.getId(), 9L, now);
        long allocationId = authorRewardService.listApprovedAllocationIds(campaign.getId(), 10).get(0);
        assertThat(authorRewardService.postPendingReward(allocationId, now).getStatus())
            .isEqualTo("POSTED_PENDING");
        AuthorRewardAllocationRow visibleReward = authorRewardService.listAuthorRewards(authorId, 10).get(0);
        assertThat(visibleReward.getPeriodCode()).isEqualTo("2199-01");
        assertThat(visibleReward.getBookName()).isEqualTo("Truyện Gamification IT");
        assertThat(visibleReward.getAmountXu()).isEqualTo(101L);
        assertThat(visibleReward.getStatus()).isEqualTo("POSTED_PENDING");
        assertThat(authorRewardService.clawback(allocationId, 9L,
            "Thu hồi theo kết quả khiếu nại", now, 7).getStatus()).isEqualTo("CLAWED_BACK");

        assertThat(jdbcTemplate.queryForList("""
            SELECT tx.id
            FROM ledger_transaction tx
                     JOIN wallet_entry entry ON entry.ledger_transaction_id = tx.id
            WHERE tx.business_id=?
              AND tx.business_type LIKE 'MONTHLY_AUTHOR_REWARD%'
            GROUP BY tx.id
            HAVING SUM(entry.amount) <> 0
            """, Long.class, "2199-01:" + bookId + ":1:" + authorId)).isEmpty();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM wallet_entry entry
                     JOIN wallet_account wallet ON wallet.id=entry.wallet_account_id
                     JOIN ledger_transaction tx ON tx.id=entry.ledger_transaction_id
            WHERE tx.business_id=? AND wallet.owner_type='AUTHOR'
            """, Integer.class, "2199-01:" + bookId + ":1:" + authorId)).isZero();
    }

    private void seedFixture(Date now) {
        jdbcTemplate.update("""
            INSERT INTO user
                (id, username, password, nick_name, account_balance, status, create_time, update_time)
            VALUES (?, 'gamification_mysql_it', 'integration-test', 'Gamification IT', 0, 0, NOW(), NOW())
            """, USER_ID);
        jdbcTemplate.update("""
            INSERT INTO author (id, user_id, pen_name, tel_phone, status, create_time)
            VALUES (?, ?, 'Tác giả Gami IT', '0997410101', 0, NOW())
            """, AUTHOR_ID, USER_ID + 1);
        jdbcTemplate.update("""
            INSERT INTO book
                (id, pic_url, book_name, author_id, author_name, book_desc, score,
                 book_status, word_count, status, update_time, create_time,
                 audit_status, cover_audit_status)
            VALUES (?, '/pic/gamification-it.png', 'Truyện Gamification IT', ?,
                    'Tác giả Gamification IT', 'Fixture có rollback', 8.0,
                    0, 1000, 1, NOW(), NOW(), 1, 1)
            """, BOOK_ID, AUTHOR_ID);
        jdbcTemplate.update("""
            INSERT INTO monthly_ticket_season
                (period_code, zone_id, start_at, end_at, vote_cutoff_at, status, policy_version)
            VALUES ('2099-10', 'Asia/Ho_Chi_Minh', ?, ?, ?, 'OPEN', 'v1')
            """, new Date(now.getTime() - 3_600_000), new Date(now.getTime() + 86_400_000),
            new Date(now.getTime() + 86_400_000));
    }

    private GamificationReadingHeartbeatService heartbeatService(Instant now) {
        return new GamificationReadingHeartbeatService(readingHeartbeatWriter,
            gamificationProgressMapper, gamificationEventProcessor,
            gamificationEventFailureWriter, gamificationProperties,
            Clock.fixed(now, ZoneOffset.UTC));
    }

    private void seedReadableChapter(long userId, long authorId, long bookId, long chapterId,
                                     long suffix) {
        String username = "heartbeat_" + suffix;
        jdbcTemplate.update("""
            INSERT INTO user
                (id, username, password, nick_name, account_balance, status, create_time, update_time)
            VALUES (?, ?, 'heartbeat-integration-test', ?, 0, 0, NOW(), NOW())
            """, userId, username, username);
        jdbcTemplate.update("""
            INSERT INTO author (id, user_id, pen_name, tel_phone, status, create_time)
            VALUES (?, ?, ?, ?, 0, NOW())
            """, authorId, userId, "HB" + suffix,
            "08" + String.format("%08d", suffix));
        jdbcTemplate.update("""
            INSERT INTO book
                (id, pic_url, book_name, author_id, author_name, book_desc, score,
                 book_status, word_count, status, update_time, create_time)
            VALUES (?, '/pic/heartbeat-it.png', ?, ?, ?, 'Fixture heartbeat',
                    7.0, 0, 100, 1, NOW(), NOW())
            """, bookId, "Truyện heartbeat " + suffix, authorId, "HB" + suffix);
        jdbcTemplate.update("""
            INSERT INTO book_index
                (id, book_id, index_num, index_name, word_count, is_vip, book_price,
                 storage_type, create_time, update_time, audit_status)
            VALUES (?, ?, 1, 'Chương heartbeat', 100, 0, 0, 'db', NOW(), NOW(), 1)
            """, chapterId, bookId);
    }

    private void deleteQuestProgressFixture(long userId) {
        jdbcTemplate.update("""
            DELETE FROM user_quest_progress
            WHERE user_id=? AND quest_code='DAILY_PAID_CHAPTER' AND period_key='2027-01-01'
            """, userId);
    }
}
