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
import org.springframework.test.annotation.Rollback;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(
    classes = FrontNovelApplication.class,
    webEnvironment = SpringBootTest.WebEnvironment.NONE,
    properties = {"spring.task.scheduling.enabled=false", "management.health.mail.enabled=false"}
)
@EnabledIfSystemProperty(named = "reader.subscription.mysql.it", matches = "true")
@Transactional
@Rollback
class ReadingSubscriptionCheckoutMySqlIntegrationTest {
    @Autowired private OrderService orderService;
    @Autowired private ReadingSubscriptionService subscriptionService;
    @Autowired private JdbcTemplate jdbcTemplate;

    @Test
    void paidCheckoutActivatesSnapshotExactlyOnceAndNeverCreditsXu() {
        long suffix = Math.floorMod(System.nanoTime(), 800_000L);
        long userId = 99_790_000_000L + suffix;
        String planCode = "PAY_" + suffix;
        insertPlan(planCode, 49_000);

        ReadingSubscriptionCheckoutCreation checkout = orderService.createSubscriptionCheckout(
            (byte) 4, userId, planCode, "checkout_" + suffix);

        assertThat(jdbcTemplate.queryForObject("""
            SELECT account_amount FROM order_pay WHERE out_trade_no=?
            """, Integer.class, checkout.outTradeNo())).isZero();
        assertThat(orderService.processPayOrder(checkout.outTradeNo(), "BANK-" + suffix,
            (byte) 4, 49_000, true)).isEqualTo(PayOrderUpdateResult.SUCCESS);
        assertThat(orderService.processPayOrder(checkout.outTradeNo(), "BANK-" + suffix,
            (byte) 4, 49_000, true)).isEqualTo(PayOrderUpdateResult.ALREADY_PROCESSED);

        assertThat(jdbcTemplate.queryForObject("""
            SELECT status FROM reading_subscription_purchase WHERE out_trade_no=?
            """, String.class, checkout.outTradeNo())).isEqualTo("ACTIVATED");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM user_reading_subscription
            WHERE user_id=? AND source_type='PAYMENT' AND source_ref=?
            """, Integer.class, userId, Long.toString(checkout.outTradeNo()))).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM ledger_transaction
            WHERE business_type='TOP_UP' AND business_id=?
            """, Integer.class, Long.toString(checkout.outTradeNo()))).isZero();
    }

    @Test
    void paidCheckoutConflictMovesToReviewWithoutCreatingSecondSubscription() {
        long suffix = Math.floorMod(System.nanoTime(), 800_000L);
        long userId = 99_800_000_000L + suffix;
        String planCode = "REVIEW_" + suffix;
        long planId = insertPlan(planCode, 59_000);
        ReadingSubscriptionCheckoutCreation checkout = orderService.createSubscriptionCheckout(
            (byte) 5, userId, planCode, "review_" + suffix);
        jdbcTemplate.update("""
            INSERT INTO user_reading_subscription
                (user_id, plan_id, plan_code_snapshot, tickets_per_period_snapshot,
                 period_months_snapshot, ticket_validity_days_snapshot, start_at, next_grant_at,
                 end_at, status, source_type, source_ref, policy_version)
            VALUES (?, ?, ?, 10, 1, 45, NOW(3), NOW(3), DATE_ADD(NOW(3), INTERVAL 1 MONTH),
                    'ACTIVE', 'ADMIN', ?, 'v1')
            """, userId, planId, planCode, "conflict-" + suffix);

        assertThat(orderService.processPayOrder(checkout.outTradeNo(), "BANK-" + suffix,
            (byte) 5, 59_000, true)).isEqualTo(PayOrderUpdateResult.SUCCESS);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT status FROM reading_subscription_purchase WHERE out_trade_no=?
            """, String.class, checkout.outTradeNo())).isEqualTo("PAID_REVIEW");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM user_reading_subscription WHERE user_id=?
            """, Integer.class, userId)).isEqualTo(1);
    }

    @Test
    void refundHandoffIsAuditedWithoutCreditingXuOrCreatingSubscription() {
        long suffix = Math.floorMod(System.nanoTime(), 800_000L);
        long userId = 99_805_000_000L + suffix;
        String planCode = "REFUND_" + suffix;
        long planId = insertPlan(planCode, 79_000);
        ReadingSubscriptionCheckoutCreation checkout = orderService.createSubscriptionCheckout(
            (byte) 5, userId, planCode, "refund_" + suffix);
        jdbcTemplate.update("""
            INSERT INTO user_reading_subscription
                (user_id, plan_id, plan_code_snapshot, tickets_per_period_snapshot,
                 period_months_snapshot, ticket_validity_days_snapshot, start_at, next_grant_at,
                 end_at, status, source_type, source_ref, policy_version)
            VALUES (?, ?, ?, 10, 1, 45, NOW(3), NOW(3), DATE_ADD(NOW(3), INTERVAL 1 MONTH),
                    'ACTIVE', 'ADMIN', ?, 'v1')
            """, userId, planId, planCode, "refund-conflict-" + suffix);
        assertThat(orderService.processPayOrder(checkout.outTradeNo(), "BANK-" + suffix,
            (byte) 5, 79_000, true)).isEqualTo(PayOrderUpdateResult.SUCCESS);

        long purchaseId = jdbcTemplate.queryForObject("""
            SELECT id FROM reading_subscription_purchase WHERE out_trade_no=?
            """, Long.class, checkout.outTradeNo());
        long version = jdbcTemplate.queryForObject("""
            SELECT version FROM reading_subscription_purchase WHERE id=?
            """, Long.class, purchaseId);
        assertThat(subscriptionService.sendPurchaseToRefund(
            purchaseId, version, 903L, "Khách hàng đã xác nhận yêu cầu hoàn tiền"))
            .isEqualTo(ReadingSubscriptionPurchaseReviewResult.REFUND_PENDING);

        assertThat(jdbcTemplate.queryForObject("""
            SELECT status FROM reading_subscription_purchase WHERE id=?
            """, String.class, purchaseId)).isEqualTo("REFUND_PENDING");
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM reading_subscription_purchase_review_audit
            WHERE purchase_id=? AND event_type='REFUND_REQUESTED'
            """, Integer.class, purchaseId)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM user_reading_subscription
            WHERE user_id=? AND source_type='PAYMENT'
            """, Integer.class, userId)).isZero();
        assertThat(jdbcTemplate.queryForObject("""
            SELECT COUNT(*) FROM ledger_transaction
            WHERE business_type='TOP_UP' AND business_id=?
            """, Integer.class, Long.toString(checkout.outTradeNo()))).isZero();
    }

    private long insertPlan(String planCode, long priceVnd) {
        jdbcTemplate.update("""
            INSERT INTO reading_subscription_plan
                (plan_code, plan_name, price_vnd, tickets_per_period, period_months,
                 ticket_validity_days, status)
            VALUES (?, 'Checkout integration plan', ?, 10, 1, 45, 'ACTIVE')
            """, planCode, priceVnd);
        return jdbcTemplate.queryForObject(
            "SELECT id FROM reading_subscription_plan WHERE plan_code=?", Long.class, planCode);
    }
}
