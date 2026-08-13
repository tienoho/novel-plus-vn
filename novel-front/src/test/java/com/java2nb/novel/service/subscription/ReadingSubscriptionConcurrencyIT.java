package com.java2nb.novel.service.subscription;

import com.java2nb.novel.FrontNovelApplication;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Không dùng transaction của test: mỗi thread phải mở transaction riêng để tranh chấp khóa và
 * unique key thật. Ledger/biên nhận kỳ có trigger chặn DELETE nên fixture dùng ID động và giữ lại
 * vài dòng audit trên database integration.
 */
@SpringBootTest(
    classes = FrontNovelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {
        "spring.task.scheduling.enabled=false",
        "management.health.mail.enabled=false"
    }
)
@EnabledIfSystemProperty(named = "reader.subscription.concurrency.it", matches = "true")
class ReadingSubscriptionConcurrencyIT {
    private static final Date START = Date.from(Instant.parse("2027-01-01T00:00:00Z"));
    private static final Date END = Date.from(Instant.parse("2027-04-01T00:00:00Z"));

    @Autowired private ReadingSubscriptionService service;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void twoConcurrentPeriodGrantsCreateOneLedgerAndOneReceipt() throws Exception {
        long userId = uniqueUserId(1);
        String planCode = seedPlan(userId, "GRANT");
        ReadingSubscriptionRow subscription = service.activate(command(
            userId, planCode, "grant-" + userId));
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<ReadingSubscriptionGrantStatus>> futures = List.of(
                executor.submit(() -> grantAfterGate(startGate, subscription.getId())),
                executor.submit(() -> grantAfterGate(startGate, subscription.getId())));

            startGate.countDown();
            List<ReadingSubscriptionGrantStatus> statuses = List.of(
                futures.get(0).get(15, TimeUnit.SECONDS),
                futures.get(1).get(15, TimeUnit.SECONDS));

            assertThat(statuses).containsExactlyInAnyOrder(
                ReadingSubscriptionGrantStatus.POSTED,
                ReadingSubscriptionGrantStatus.NOT_DUE);
            assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reading_ticket_ledger
                WHERE user_id=? AND entry_type='GRANT' AND business_type='SUBSCRIPTION'
                """, Integer.class, userId)).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM reading_subscription_period_grant
                WHERE subscription_id=?
                """, Integer.class, subscription.getId())).isEqualTo(1);
            assertThat(jdbcTemplate.queryForObject("""
                SELECT available_balance FROM reading_ticket_account WHERE user_id=?
                """, Long.class, userId)).isEqualTo(10L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void concurrentActivationReplayCreatesOneSubscription() throws Exception {
        long userId = uniqueUserId(2);
        String planCode = seedPlan(userId, "ACTIVATE");
        ReadingSubscriptionActivationCommand command = command(
            userId, planCode, "activate-" + userId);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            List<Future<ReadingSubscriptionRow>> futures = List.of(
                executor.submit(() -> activateAfterGate(startGate, command)),
                executor.submit(() -> activateAfterGate(startGate, command)));

            startGate.countDown();
            ReadingSubscriptionRow first = futures.get(0).get(15, TimeUnit.SECONDS);
            ReadingSubscriptionRow second = futures.get(1).get(15, TimeUnit.SECONDS);

            assertThat(first.getId()).isEqualTo(second.getId());
            assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*) FROM user_reading_subscription
                WHERE source_type='ADMIN' AND source_ref=?
                """, Integer.class, command.sourceRef())).isEqualTo(1);
        } finally {
            executor.shutdownNow();
        }
    }

    private ReadingSubscriptionGrantStatus grantAfterGate(CountDownLatch startGate,
                                                           long subscriptionId)
        throws InterruptedException {
        startGate.await();
        return service.grantDuePeriod(subscriptionId, START, ZoneId.of("UTC"), "v1").status();
    }

    private ReadingSubscriptionRow activateAfterGate(
        CountDownLatch startGate, ReadingSubscriptionActivationCommand command)
        throws InterruptedException {
        startGate.await();
        return service.activate(command);
    }

    private ReadingSubscriptionActivationCommand command(long userId, String planCode,
                                                          String sourceRef) {
        return new ReadingSubscriptionActivationCommand(
            userId, planCode, START, END, "ADMIN", sourceRef, "v1");
    }

    private String seedPlan(long userId, String discriminator) {
        String planCode = "CI_" + discriminator + '_' + userId;
        jdbcTemplate.update("""
            INSERT INTO reading_subscription_plan
                (plan_code, plan_name, tickets_per_period, period_months,
                 ticket_validity_days, status)
            VALUES (?, 'Concurrency integration plan', 10, 1, 45, 'ACTIVE')
            """, planCode);
        return planCode;
    }

    private long uniqueUserId(long discriminator) {
        return 99_790_000_000L
            + Math.floorMod(System.nanoTime(), 800_000L) * 10
            + discriminator;
    }
}
