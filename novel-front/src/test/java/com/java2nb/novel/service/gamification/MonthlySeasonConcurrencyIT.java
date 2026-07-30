package com.java2nb.novel.service.gamification;

import com.java2nb.novel.FrontNovelApplication;
import com.java2nb.novel.mapper.MonthlyRankingMapper;
import com.java2nb.novel.service.impl.MonthlyRankingBatchWriter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Cố ý không dùng Transactional/Rollback: hai caller phải tranh atomic UPDATE trên MySQL thật.
 * Snapshot/entry/vote có trigger chống DELETE, vì vậy fixture dùng ID động và giữ lại audit data.
 */
@SpringBootTest(
    classes = FrontNovelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.task.scheduling.enabled=false",
        "management.health.mail.enabled=false"
    }
)
@EnabledIfSystemProperty(named = "gamification.concurrency.it", matches = "true")
@Import(MonthlySeasonConcurrencyIT.BatchFailureConfiguration.class)
class MonthlySeasonConcurrencyIT {

    private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    @Autowired
    private MonthlyRankingService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private FailureInjectingBatchWriter batchWriter;

    @AfterEach
    void resetFailureInjection() {
        batchWriter.disableFailure();
    }

    @Test
    void twoConcurrentClosersCreateExactlyOneDeterministicSnapshot() throws Exception {
        long suffix = Math.floorMod(System.nanoTime(), 900_000);
        int year = 3000 + (int) (suffix % 6_000);
        int month = 1 + (int) ((suffix / 6_000) % 12);
        String period = String.format("%04d-%02d", year, month);
        LocalDateTime startLocal = LocalDateTime.of(year, month, 1, 0, 0);
        LocalDateTime endLocal = startLocal.plusMonths(1);
        Date start = Date.from(startLocal.atZone(ZONE).toInstant());
        Date cutoff = Date.from(endLocal.atZone(ZONE).toInstant());
        Date closeAt = new Date(cutoff.getTime() + 1_000L);
        long seasonId = insertSeason(period, start, cutoff);
        long bookBase = 99_760_000_000L + suffix * 10;
        seedVotes(seasonId, bookBase, cutoff, suffix);
        assertThat(service.reconcile(seasonId)).hasSize(3);

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<SeasonPhaseResult.Outcome>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 2; index++) {
                String owner = "season-ci-" + suffix + '-' + index;
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return service.closeSeason(
                        seasonId, closeAt, owner, 0, 300, 2).outcome();
                }));
            }
            startGate.countDown();
            List<SeasonPhaseResult.Outcome> outcomes = new ArrayList<>();
            for (Future<SeasonPhaseResult.Outcome> future : futures) {
                outcomes.add(future.get());
            }

            assertThat(outcomes).containsExactlyInAnyOrder(
                SeasonPhaseResult.Outcome.OWNER, SeasonPhaseResult.Outcome.NOT_OWNER);
            assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM monthly_rank_snapshot WHERE season_id=?",
                Integer.class, seasonId)).isEqualTo(1);
            Long snapshotId = jdbcTemplate.queryForObject(
                "SELECT snapshot_id FROM monthly_ticket_season WHERE id=?",
                Long.class, seasonId);
            assertThat(snapshotId).isNotNull();
            assertThat(jdbcTemplate.queryForObject(
                "SELECT status FROM monthly_ticket_season WHERE id=?",
                String.class, seasonId)).isEqualTo("REVIEW");
            assertThat(jdbcTemplate.queryForObject(
                "SELECT content_hash FROM monthly_rank_snapshot WHERE id=?",
                String.class, snapshotId)).hasSize(64);
            assertThat(jdbcTemplate.queryForList(
                "SELECT book_id FROM monthly_rank_entry WHERE snapshot_id=? ORDER BY rank_no",
                Long.class, snapshotId)).containsExactly(bookBase + 2, bookBase + 1, bookBase + 3);

            assertThat(service.finalizeSeason(seasonId, 9L,
                new Date(closeAt.getTime() + 1_000L)).outcome())
                .isEqualTo(SeasonPhaseResult.Outcome.OWNER);
            assertThatThrownBy(() -> jdbcTemplate.update(
                "UPDATE monthly_ticket_season SET status='REVIEW' WHERE id=?", seasonId))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("invalid monthly_ticket_season status transition");

            verifyPauseAndRetryPath(year + 1, month, suffix + 1);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void failedThirdBatchResumesFromCheckpointAndMatchesOneShotHash() {
        long suffix = Math.floorMod(System.nanoTime(), 800_000) + 1_000_000;
        int year = 3000 + (int) (suffix % 5_000);
        int month = 1 + (int) ((suffix / 5_000) % 12);
        String failedPeriod = String.format("%04d-%02d", year, month);
        String directPeriod = String.format("%04d-%02d", year + 1, month);
        LocalDateTime startLocal = LocalDateTime.of(year, month, 1, 0, 0);
        Date start = Date.from(startLocal.atZone(ZONE).toInstant());
        Date cutoff = Date.from(startLocal.plusMonths(1).atZone(ZONE).toInstant());
        Date closeAt = new Date(cutoff.getTime() + 1_000L);
        long failedSeasonId = insertSeason(failedPeriod, start, cutoff);
        long directSeasonId = insertSeason(
            directPeriod, new Date(start.getTime() + 1_000L), cutoff);
        long bookBase = 99_780_000_000L + (suffix % 1_000_000) * 10;
        seedVotes(failedSeasonId, bookBase, cutoff, suffix);
        seedVotes(directSeasonId, bookBase, cutoff, suffix + 1);

        assertThat(service.closeSeason(failedSeasonId, closeAt).outcome())
            .isEqualTo(SeasonPhaseResult.Outcome.OWNER);
        batchWriter.failOnInvocation(3);
        assertThatThrownBy(() -> service.buildSnapshot(failedSeasonId,
            "season-fail-ci-" + suffix, closeAt, 0, 300, 1))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("batch 3");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT checkpoint FROM scheduled_job_run
            WHERE job_type='SEASON_SNAPSHOT' AND scope_type='SEASON' AND scope_key=?
            """, String.class, Long.toString(failedSeasonId))).isEqualTo("2");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM monthly_rank_entry entry
            JOIN monthly_rank_snapshot snapshot ON snapshot.id=entry.snapshot_id
            WHERE snapshot.season_id=?
            """, Integer.class, failedSeasonId)).isEqualTo(2);

        batchWriter.disableFailure();
        assertThat(service.retrySnapshot(failedSeasonId, "season-retry-ci-" + suffix,
            closeAt, 0, 300, 1).outcome()).isEqualTo(SeasonPhaseResult.Outcome.OWNER);
        jdbcTemplate.update("""
            UPDATE scheduled_job_run
            SET status='FAILED', finished_at=?, error_message='simulated post-review crash'
            WHERE job_type='SEASON_SNAPSHOT' AND scope_type='SEASON' AND scope_key=?
            """, closeAt, Long.toString(failedSeasonId));
        assertThat(service.buildSnapshot(failedSeasonId, "season-recovery-ci-" + suffix,
            closeAt, 0, 300, 1).outcome()).isEqualTo(SeasonPhaseResult.Outcome.COMPLETED);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT status FROM scheduled_job_run
            WHERE job_type='SEASON_SNAPSHOT' AND scope_type='SEASON' AND scope_key=?
            """, String.class, Long.toString(failedSeasonId))).isEqualTo("SUCCEEDED");
        assertThat(service.closeSeason(directSeasonId, closeAt, "season-direct-ci-" + suffix,
            0, 300, 1).outcome()).isEqualTo(SeasonPhaseResult.Outcome.OWNER);

        String failedHash = jdbcTemplate.queryForObject("""
            SELECT snapshot.content_hash
            FROM monthly_rank_snapshot snapshot
            WHERE snapshot.season_id=?
            """, String.class, failedSeasonId);
        String directHash = jdbcTemplate.queryForObject("""
            SELECT snapshot.content_hash
            FROM monthly_rank_snapshot snapshot
            WHERE snapshot.season_id=?
            """, String.class, directSeasonId);
        assertThat(failedHash).isEqualTo(directHash).hasSize(64);
    }

    private void verifyPauseAndRetryPath(int year, int month, long suffix) {
        String period = String.format("%04d-%02d", year, month);
        LocalDateTime startLocal = LocalDateTime.of(year, month, 1, 0, 0);
        Date start = Date.from(startLocal.atZone(ZONE).toInstant());
        Date cutoff = Date.from(startLocal.plusMonths(1).atZone(ZONE).toInstant());
        Date closeAt = new Date(cutoff.getTime() + 1_000L);
        long seasonId = insertSeason(period, start, cutoff);

        assertThat(service.closeSeason(seasonId, closeAt).outcome())
            .isEqualTo(SeasonPhaseResult.Outcome.OWNER);
        assertThat(service.pauseSnapshot(seasonId, 9L,
            "Tạm dừng để kiểm tra tích hợp", closeAt).outcome())
            .isEqualTo(SeasonPhaseResult.Outcome.OWNER);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT status FROM scheduled_job_run
            WHERE job_type='SEASON_SNAPSHOT' AND scope_type='SEASON' AND scope_key=?
            """, String.class, Long.toString(seasonId))).isEqualTo("PAUSED");

        SeasonPhaseResult retried = service.retrySnapshot(seasonId,
            "season-retry-ci-" + suffix, closeAt, 0, 300, 2);

        assertThat(retried.outcome()).isEqualTo(SeasonPhaseResult.Outcome.OWNER);
        assertThat(retried.seasonStatus()).isEqualTo("REVIEW");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT status FROM scheduled_job_run
            WHERE job_type='SEASON_SNAPSHOT' AND scope_type='SEASON' AND scope_key=?
            """, String.class, Long.toString(seasonId))).isEqualTo("SUCCEEDED");
    }

    private long insertSeason(String period, Date start, Date cutoff) {
        jdbcTemplate.update("""
            INSERT INTO monthly_ticket_season
                (period_code, zone_id, start_at, end_at, vote_cutoff_at, status, policy_version)
            VALUES (?, 'Asia/Ho_Chi_Minh', ?, ?, ?, 'OPEN', 'v1')
            """, period, start, cutoff, cutoff);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM monthly_ticket_season WHERE period_code=?", Long.class, period);
    }

    private void seedVotes(long seasonId, long bookBase, Date cutoff, long suffix) {
        Date early = new Date(cutoff.getTime() - 20_000L);
        Date late = new Date(cutoff.getTime() - 10_000L);
        insertVote(seasonId, bookBase + 1, 501L, 701L, 3, early, suffix, 1);
        insertVote(seasonId, bookBase + 1, 501L, 702L, 2, late, suffix, 2);
        insertVote(seasonId, bookBase + 2, 502L, 703L, 3, early, suffix, 3);
        insertVote(seasonId, bookBase + 2, 502L, 704L, 2, early, suffix, 4);
        insertVote(seasonId, bookBase + 3, 503L, 705L, 5, early, suffix, 5);
    }

    private void insertVote(long seasonId, long bookId, long authorId, long userId,
                            long ticketCount, Date createTime, long suffix, int sequence) {
        String token = suffix + "-" + sequence;
        jdbcTemplate.update("""
            INSERT INTO monthly_ticket_vote
                (season_id, book_id, author_id, user_id, ticket_count, ledger_id,
                 idempotency_key, request_hash, client_request_id, status,
                 source_ip_hash, policy_version, create_time, update_time)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'VALID', ?, 'v1', ?, ?)
            """, seasonId, bookId, authorId, userId, ticketCount,
            99_770_000_000L + suffix * 10 + sequence,
            "SEASON-CI:" + token, "d".repeat(64), "season-ci-" + token,
            "e".repeat(64), createTime, createTime);
    }

    @TestConfiguration
    static class BatchFailureConfiguration {
        @Bean
        @Primary
        FailureInjectingBatchWriter failureInjectingBatchWriter(MonthlyRankingMapper mapper) {
            return new FailureInjectingBatchWriter(mapper);
        }
    }

    static class FailureInjectingBatchWriter extends MonthlyRankingBatchWriter {
        private final AtomicInteger invocation = new AtomicInteger();
        private volatile int failAt = Integer.MAX_VALUE;

        FailureInjectingBatchWriter(MonthlyRankingMapper mapper) {
            super(mapper);
        }

        void failOnInvocation(int number) {
            invocation.set(0);
            failAt = number;
        }

        void disableFailure() {
            invocation.set(0);
            failAt = Integer.MAX_VALUE;
        }

        @Override
        @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
        public void append(String jobType, String scopeType, String scopeKey, String ownerInstance,
                           List<MonthlyRankRow> rows, long nextOffset, Date heartbeatAt) {
            if (invocation.incrementAndGet() == failAt) {
                throw new IllegalStateException("Lỗi kiểm thử tại batch 3");
            }
            super.append(jobType, scopeType, scopeKey, ownerInstance, rows, nextOffset, heartbeatAt);
        }
    }
}
