package com.java2nb.novel.service.entitlement;

import com.java2nb.novel.FrontNovelApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Không dùng transaction của test: hai thread phải mở hai transaction thật để chứng minh khóa
 * tài khoản serialize thao tác mở cùng một chương. Fixture dùng ID động vì bảng audit bất biến.
 */
@SpringBootTest(
    classes = FrontNovelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.task.scheduling.enabled=false",
        "management.health.mail.enabled=false"
    }
)
@EnabledIfSystemProperty(named = "reader.ticket.concurrency.it", matches = "true")
class ReadingTicketConcurrencyIT {

    @Autowired
    private ReadingTicketService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void twoConcurrentUnlocksSpendExactlyOneTicket() throws Exception {
        long suffix = Math.floorMod(System.nanoTime(), 800_000L);
        long userId = 99_770_000_000L + suffix;
        long bookId = 99_771_000_000L + suffix;
        long chapterId = 99_772_000_000L + suffix;
        Date at = Date.from(Instant.parse("2099-11-15T00:00:00Z"));
        service.grant(new ReadingTicketGrantCommand(userId, 1, "SUBSCRIPTION",
            "concurrency-" + suffix, "READING_TICKET:CONCURRENCY:GRANT:" + userId,
            new Date(at.getTime() - 60_000), new Date(at.getTime() + 86_400_000),
            "SYSTEM", null, "Concurrency integration test", "v1"));

        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        List<Future<ReadingTicketPostResult>> futures = new ArrayList<>();
        try {
            for (int index = 0; index < 2; index++) {
                String clientRequestId = "concurrent_" + suffix + '_' + index;
                futures.add(executor.submit(() -> {
                    startGate.await();
                    return service.unlockChapter(new ReadingTicketUnlockCommand(
                        userId, bookId, chapterId, clientRequestId, at, "v1", 20)).status();
                }));
            }
            startGate.countDown();
            List<ReadingTicketPostResult> outcomes = futures.stream().map(future -> {
                try {
                    return future.get();
                } catch (Exception exception) {
                    throw new IllegalStateException(exception);
                }
            }).toList();

            assertThat(outcomes).containsExactlyInAnyOrder(
                ReadingTicketPostResult.POSTED, ReadingTicketPostResult.ALREADY_ENTITLED);
            assertThat(jdbcTemplate.queryForObject(
                "SELECT available_balance FROM reading_ticket_account WHERE user_id=?",
                Long.class, userId)).isZero();
            assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reading_ticket_ledger
                WHERE user_id=? AND entry_type='SPEND'
                """, Integer.class, userId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM chapter_entitlement
                WHERE user_id=? AND book_index_id=? AND status='ACTIVE'
                """, Integer.class, userId, chapterId)).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }
}
