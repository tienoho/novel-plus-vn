package com.java2nb.novel.core.observability;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

class NovelBusinessMetricsTest {

    @Test
    void exposesBoundedBusinessMetricsWithoutDynamicIdentifiers() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NovelBusinessMetrics metrics = new NovelBusinessMetrics(registry);

        metrics.setLedgerMismatch(NovelBusinessMetrics.LedgerCheck.ZERO_SUM, 3);
        metrics.setRenewalQueue(NovelBusinessMetrics.RenewalQueue.PAST_DUE, 2);
        metrics.setGamificationQueue(NovelBusinessMetrics.GamificationQueue.PENDING_EVENTS, 11);
        metrics.recordPayment(NovelBusinessMetrics.PaymentProvider.VNPAY,
            NovelBusinessMetrics.PaymentOperation.WEBHOOK,
            NovelBusinessMetrics.Outcome.SUCCESS);

        assertThat(registry.get("novel_ledger_mismatch_records")
            .tag("check", "zero_sum").gauge().value()).isEqualTo(3);
        assertThat(registry.get("novel_subscription_renewal_queue")
            .tag("state", "past_due").gauge().value()).isEqualTo(2);
        assertThat(registry.get("novel_gamification_queue")
            .tag("state", "pending_events").gauge().value()).isEqualTo(11);
        assertThat(registry.get("novel_payment_operations")
            .tags("provider", "vnpay", "operation", "webhook", "outcome", "success")
            .counter().count()).isEqualTo(1);
    }

    @Test
    void clampsGaugeValuesToZero() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        NovelBusinessMetrics metrics = new NovelBusinessMetrics(registry);

        metrics.setRenewalQueue(NovelBusinessMetrics.RenewalQueue.DUE, -5);

        assertThat(registry.get("novel_subscription_renewal_queue")
            .tag("state", "due").gauge().value()).isZero();
    }
}
