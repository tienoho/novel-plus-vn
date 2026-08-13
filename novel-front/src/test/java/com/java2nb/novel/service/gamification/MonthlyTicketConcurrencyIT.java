package com.java2nb.novel.service.gamification;

import com.java2nb.novel.FrontNovelApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test này cố ý không dùng {@code @Transactional}/{@code @Rollback}: hai thread phải mở hai
 * transaction database thật mới chứng minh được khóa {@code FOR UPDATE}. Ledger/lot có trigger
 * chặn DELETE nên fixture dùng ID sinh động và để lại vài dòng audit trên database integration.
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
class MonthlyTicketConcurrencyIT {

    @Autowired
    private MonthlyTicketService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void twoConcurrentVotesCannotSpendOneTicketTwice() throws Exception {
        long suffix = Math.floorMod(System.nanoTime(), 900_000);
        long userId = 99_740_000_000L + suffix;
        long authorId = 99_741_000_000L + suffix;
        long bookId = 99_742_000_000L + suffix;
        Date voteAt = new Date(4_098_614_400_000L); // 2099-11-15T00:00:00Z
        seedFixture(userId, authorId, bookId);
        long seasonId = jdbcTemplate.queryForObject(
            "SELECT id FROM monthly_ticket_season WHERE period_code='2099-11'", Long.class);
        service.grant(new TicketGrantCommand(userId, 1, "ADMIN_GRANT", "concurrency-" + suffix,
            "ADMIN_GRANT:concurrency:" + userId, new Date(voteAt.getTime() - 60_000),
            new Date(voteAt.getTime() + 86_400_000), "ADMIN", 1L, "concurrency IT", "v1", 1L));

        TicketPolicy policy = new TicketPolicy("v1", 60, 10, 20, 50, 100, 50, false);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<String>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 2; index++) {
                String clientRequestId = "concurrency-" + suffix + '-' + index;
                futures.add(executor.submit(() -> {
                    startGate.await();
                    try {
                        return service.castVote(new TicketVoteCommand(userId, bookId, seasonId, 1,
                            clientRequestId, "c".repeat(64), "d".repeat(64), "v1", voteAt,
                            LocalDate.of(2099, 11, 15), 1L), policy).status().name();
                    } catch (RuntimeException exception) {
                        return exception.getClass().getSimpleName();
                    }
                }));
            }
            startGate.countDown();
            List<String> outcomes = futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }).toList();

            assertThat(outcomes).containsExactlyInAnyOrder("POSTED", "BusinessException");
            assertThat(jdbcTemplate.queryForObject(
                "SELECT available_balance FROM monthly_ticket_account WHERE user_id=?",
                Long.class, userId)).isZero();
            assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM monthly_ticket_vote WHERE user_id=?",
                Integer.class, userId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private void seedFixture(long userId, long authorId, long bookId) {
        jdbcTemplate.update("""
            INSERT INTO user
                (id, username, password, nick_name, account_balance, status, create_time, update_time)
            VALUES (?, ?, 'concurrency-test', 'Concurrency IT', 0, 0, NOW(), NOW())
            """, userId, "gamify_ci_" + userId);
        jdbcTemplate.update("""
            INSERT INTO author (id, user_id, pen_name, tel_phone, status, create_time)
            VALUES (?, ?, ?, ?, 0, NOW())
            """, authorId, userId + 1, "TG CI " + (authorId % 1_000_000),
            "09" + (10_000_000L + userId % 90_000_000L));
        jdbcTemplate.update("""
            INSERT INTO book
                (id, pic_url, book_name, author_id, author_name, book_desc, score,
                 book_status, word_count, status, update_time, create_time,
                 audit_status, cover_audit_status)
            VALUES (?, '/pic/gamification-ci.png', ?, ?, ?, 'Concurrency fixture', 8.0,
                    0, 1000, 1, NOW(), NOW(), 1, 1)
            """, bookId, "Truyện CI " + bookId, authorId, "Tác giả CI " + authorId);
        jdbcTemplate.update("""
            INSERT IGNORE INTO monthly_ticket_season
                (period_code, zone_id, start_at, end_at, vote_cutoff_at, status, policy_version)
            VALUES ('2099-11', 'Asia/Ho_Chi_Minh', '2099-11-01 00:00:00.000',
                    '2099-12-01 00:00:00.000', '2099-12-01 00:00:00.000', 'OPEN', 'v1')
            """);
    }
}
