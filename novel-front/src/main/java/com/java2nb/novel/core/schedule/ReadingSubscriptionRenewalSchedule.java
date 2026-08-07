package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.ReaderEntitlementProperties;
import com.java2nb.novel.core.observability.NovelBusinessMetrics;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.Outcome;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.RenewalQueue;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalService;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalResult;
import com.java2nb.novel.service.VnpayRecurringRenewalProcessor;
import com.java2nb.novel.service.wallet.InsufficientWalletBalanceException;
import java.time.Clock;
import java.time.ZoneId;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class ReadingSubscriptionRenewalSchedule {
    private final ReaderEntitlementProperties properties;
    private final ReadingSubscriptionRenewalService service;
    private final VnpayRecurringRenewalProcessor vnpayRecurringRenewalProcessor;
    private final NovelBusinessMetrics metrics;
    private final Clock clock = Clock.systemUTC();

    @Scheduled(fixedDelayString = "${novel.reader-entitlement.subscription-renewal-delay-ms:60000}")
    public void renewDueSubscriptions() {
        if (!properties.isEnabled() || !properties.isConfigured()
            || !properties.isSubscriptionRenewalEnabled()) {
            return;
        }
        Date now = Date.from(clock.instant());
        ZoneId zoneId = ZoneId.of(properties.getSubscriptionZoneId());
        for (Long subscriptionId : service.listDueSubscriptionIds(
            now, properties.getSubscriptionRenewalBatchSize())) {
            try {
                record(service.prepareCycle(subscriptionId, now, zoneId));
            } catch (RuntimeException exception) {
                metrics.recordRenewal(Outcome.FAILED);
                log.error("SUBSCRIPTION-RENEWAL-001 không thể chuẩn bị cycle: subscriptionId={}",
                    subscriptionId, exception);
            }
        }
        for (Long cycleId : service.listDueCycleIds(
            now, properties.getSubscriptionRenewalBatchSize())) {
            try {
                ReadingSubscriptionRenewalResult result = service.processCycle(cycleId, now);
                record(result);
                if (result == ReadingSubscriptionRenewalResult.PROVIDER_CLAIMED) {
                    record(vnpayRecurringRenewalProcessor.process(cycleId, now));
                }
            } catch (InsufficientWalletBalanceException exception) {
                try {
                    record(service.recordWalletFailure(cycleId, now));
                } catch (RuntimeException recordException) {
                    metrics.recordRenewal(Outcome.FAILED);
                    log.error("SUBSCRIPTION-RENEWAL-003 không thể ghi lịch thử lại: cycleId={}",
                        cycleId, recordException);
                }
            } catch (RuntimeException exception) {
                metrics.recordRenewal(Outcome.FAILED);
                log.error("SUBSCRIPTION-RENEWAL-002 xử lý cycle thất bại: cycleId={}",
                    cycleId, exception);
            }
        }
        refreshQueueMetrics();
    }

    private void refreshQueueMetrics() {
        try {
            metrics.setRenewalQueue(RenewalQueue.DUE, service.countCyclesByStatus("DUE"));
            metrics.setRenewalQueue(RenewalQueue.PROVIDER_PENDING,
                service.countCyclesByStatus("PROVIDER_PENDING"));
            metrics.setRenewalQueue(RenewalQueue.RETRY_WAIT,
                service.countCyclesByStatus("RETRY_WAIT"));
            metrics.setRenewalQueue(RenewalQueue.PAST_DUE, service.countPastDueSubscriptions());
        } catch (RuntimeException exception) {
            metrics.recordRenewal(Outcome.FAILED);
            log.error("SUBSCRIPTION-RENEWAL-004 không thể cập nhật metric hàng đợi", exception);
        }
    }

    private void record(ReadingSubscriptionRenewalResult result) {
        Outcome outcome = switch (result) {
            case SETTLED, CYCLE_CREATED -> Outcome.SUCCESS;
            case NOT_DUE -> Outcome.ALREADY_PROCESSED;
            case GRACE_EXPIRED -> Outcome.FAILED;
            case PRICE_CONSENT_REQUIRED, PROVIDER_CLAIMED, PROVIDER_PENDING, RETRY_SCHEDULED ->
                Outcome.PENDING;
        };
        metrics.recordRenewal(outcome);
    }
}
