package com.java2nb.novel.core.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;
import org.springframework.stereotype.Component;

/**
 * Metric nghiệp vụ có tập nhãn hữu hạn để tránh cardinality cao và không đưa PII vào hệ thống giám sát.
 */
@Component
public class NovelBusinessMetrics {

    public enum PaymentProvider {
        VNPAY, VIETQR, VNPAY_RECURRING
    }

    public enum PaymentOperation {
        WEBHOOK, QUERY, RENEWAL
    }

    public enum Outcome {
        SUCCESS, ALREADY_PROCESSED, PENDING, FAILED, REJECTED, UNAVAILABLE
    }

    public enum LedgerCheck {
        ZERO_SUM, WALLET_PROJECTION, TICKET_PROJECTION, TICKET_ALLOCATION,
        ORPHAN_LOT, RANK_COUNTER
    }

    public enum RenewalQueue {
        DUE, PROVIDER_PENDING, RETRY_WAIT, PAST_DUE
    }

    public enum GamificationQueue {
        PENDING_EVENTS, PENDING_REWARDS, STALE_JOBS, REVIEW_OVERDUE
    }

    private final MeterRegistry registry;
    private final Map<LedgerCheck, AtomicLong> ledgerMismatches = new EnumMap<>(LedgerCheck.class);
    private final Map<RenewalQueue, AtomicLong> renewalQueue = new EnumMap<>(RenewalQueue.class);
    private final Map<GamificationQueue, AtomicLong> gamificationQueue =
        new EnumMap<>(GamificationQueue.class);

    public NovelBusinessMetrics(MeterRegistry registry) {
        this.registry = registry;
        for (LedgerCheck check : LedgerCheck.values()) {
            AtomicLong value = new AtomicLong();
            ledgerMismatches.put(check, value);
            registry.gauge("novel_ledger_mismatch_records",
                java.util.List.of(io.micrometer.core.instrument.Tag.of("check", label(check))),
                value);
        }
        for (RenewalQueue state : RenewalQueue.values()) {
            AtomicLong value = new AtomicLong();
            renewalQueue.put(state, value);
            registry.gauge("novel_subscription_renewal_queue",
                java.util.List.of(io.micrometer.core.instrument.Tag.of("state", label(state))),
                value);
        }
        for (GamificationQueue state : GamificationQueue.values()) {
            AtomicLong value = new AtomicLong();
            gamificationQueue.put(state, value);
            registry.gauge("novel_gamification_queue",
                java.util.List.of(io.micrometer.core.instrument.Tag.of("state", label(state))),
                value);
        }
    }

    public void recordPayment(PaymentProvider provider, PaymentOperation operation, Outcome outcome) {
        Counter.builder("novel_payment_operations")
            .description("Số thao tác thanh toán theo provider và kết quả")
            .tag("provider", label(provider))
            .tag("operation", label(operation))
            .tag("outcome", label(outcome))
            .register(registry)
            .increment();
    }

    public void recordRenewal(Outcome outcome) {
        Counter.builder("novel_subscription_renewal_operations")
            .description("Số cycle gia hạn đã xử lý theo kết quả")
            .tag("outcome", label(outcome))
            .register(registry)
            .increment();
    }

    public void recordGamificationEvent(Outcome outcome) {
        Counter.builder("novel_gamification_events_processed")
            .description("Số event gamification worker đã xử lý theo kết quả")
            .tag("outcome", label(outcome))
            .register(registry)
            .increment();
    }

    public void setLedgerMismatch(LedgerCheck check, long count) {
        ledgerMismatches.get(check).set(nonNegative(count));
    }

    public void setRenewalQueue(RenewalQueue state, long count) {
        renewalQueue.get(state).set(nonNegative(count));
    }

    public void setGamificationQueue(GamificationQueue state, long count) {
        gamificationQueue.get(state).set(nonNegative(count));
    }

    private long nonNegative(long value) {
        return Math.max(0L, value);
    }

    private String label(Enum<?> value) {
        return value.name().toLowerCase(java.util.Locale.ROOT);
    }
}
