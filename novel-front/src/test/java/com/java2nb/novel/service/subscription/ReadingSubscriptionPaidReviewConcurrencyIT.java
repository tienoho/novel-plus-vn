package com.java2nb.novel.service.subscription;

import com.java2nb.novel.FrontNovelApplication;
import com.java2nb.novel.service.OrderService;
import com.java2nb.novel.service.PayOrderUpdateResult;
import com.java2nb.novel.service.ReadingSubscriptionCheckoutCreation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = FrontNovelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"spring.task.scheduling.enabled=false", "management.health.mail.enabled=false"}
)
@EnabledIfSystemProperty(named = "reader.subscription.mysql.it", matches = "true")
class ReadingSubscriptionPaidReviewConcurrencyIT {
    @Autowired private OrderService orderService;
    @Autowired private ReadingSubscriptionService subscriptionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void twoReviewersCanActivatePaidReviewOnlyOnce() throws Exception {
        long suffix = Math.floorMod(System.nanoTime(), 800_000L);
        long userId = 99_810_000_000L + suffix;
        String planCode = "RETRY_" + suffix;
        long planId = insertPlan(planCode);
        ReadingSubscriptionCheckoutCreation checkout = orderService.createSubscriptionCheckout(
            (byte) 4, userId, planCode, "retry_" + suffix);
        jdbcTemplate.update("""
            INSERT INTO user_reading_subscription
                (user_id, plan_id, plan_code_snapshot, tickets_per_period_snapshot,
                 period_months_snapshot, ticket_validity_days_snapshot, start_at, next_grant_at,
                 end_at, status, source_type, source_ref, policy_version)
            VALUES (?, ?, ?, 10, 1, 45, NOW(3), NOW(3), DATE_ADD(NOW(3), INTERVAL 1 MONTH),
                    'ACTIVE', 'ADMIN', ?, 'v1')
            """, userId, planId, planCode, "review-conflict-" + suffix);
        assertThat(orderService.processPayOrder(checkout.outTradeNo(), "BANK-" + suffix,
            (byte) 4, 69_000, true)).isEqualTo(PayOrderUpdateResult.SUCCESS);
        jdbcTemplate.update("""
            UPDATE user_reading_subscription SET status='EXPIRED'
            WHERE user_id=? AND source_type='ADMIN' AND source_ref=?
            """, userId, "review-conflict-" + suffix);

        List<Long> purchaseState = jdbcTemplate.queryForObject("""
            SELECT id, version FROM reading_subscription_purchase WHERE out_trade_no=?
            """, (resultSet, rowNum) -> List.of(resultSet.getLong(1), resultSet.getLong(2)),
            checkout.outTradeNo());
        long purchaseId = purchaseState.get(0);
        long expectedVersion = purchaseState.get(1);
        CountDownLatch startGate = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Object> first = executor.submit(() -> retry(
                startGate, purchaseId, expectedVersion, 901L));
            Future<Object> second = executor.submit(() -> retry(
                startGate, purchaseId, expectedVersion, 902L));
            startGate.countDown();
            List<Object> outcomes = List.of(first.get(), second.get());

            assertThat(outcomes).filteredOn(ReadingSubscriptionPurchaseReviewResult.ACTIVATED::equals)
                .hasSize(1);
            assertThat(outcomes).filteredOn(IllegalStateException.class::isInstance).hasSize(1);
        } finally {
            executor.shutdownNow();
        }

        assertThat(jdbcTemplate.queryForObject("""
            SELECT status FROM reading_subscription_purchase WHERE id=?
            """, String.class, purchaseId)).isEqualTo("ACTIVATED");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM user_reading_subscription
            WHERE user_id=? AND source_type='PAYMENT' AND source_ref=?
            """, Integer.class, userId, Long.toString(checkout.outTradeNo()))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM reading_subscription_purchase_review_audit
            WHERE purchase_id=? AND event_type='RETRY_ACTIVATED'
            """, Integer.class, purchaseId)).isEqualTo(1);
    }

    private Object retry(CountDownLatch startGate, long purchaseId,
                         long expectedVersion, long operatorId) throws InterruptedException {
        startGate.await();
        try {
            return subscriptionService.retryPurchaseActivation(
                purchaseId, expectedVersion, operatorId, "Đã đối soát giao dịch ngân hàng");
        } catch (RuntimeException exception) {
            return exception;
        }
    }

    private long insertPlan(String planCode) {
        jdbcTemplate.update("""
            INSERT INTO reading_subscription_plan
                (plan_code, plan_name, price_vnd, tickets_per_period, period_months,
                 ticket_validity_days, status)
            VALUES (?, 'Paid review concurrency plan', 69000, 10, 1, 45, 'ACTIVE')
            """, planCode);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM reading_subscription_plan WHERE plan_code=?", Long.class, planCode);
    }
}
