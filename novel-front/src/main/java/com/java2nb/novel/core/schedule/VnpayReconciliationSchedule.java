package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.VnpayProperties;
import com.java2nb.novel.core.observability.NovelBusinessMetrics;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.Outcome;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.PaymentOperation;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.PaymentProvider;
import com.java2nb.novel.service.OrderService;
import com.java2nb.novel.service.PayOrderSnapshot;
import com.java2nb.novel.service.PayOrderUpdateResult;
import com.java2nb.novel.service.VnpayQueryResult;
import com.java2nb.novel.service.VnpayQueryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class VnpayReconciliationSchedule {

    private static final byte VNPAY_CHANNEL = 4;

    private final VnpayProperties properties;
    private final OrderService orderService;
    private final VnpayQueryService queryService;
    private final NovelBusinessMetrics metrics;

    @Scheduled(
        fixedDelayString = "${vnpay.reconciliation-delay-ms:300000}",
        initialDelayString = "${vnpay.reconciliation-initial-delay-ms:60000}"
    )
    public void reconcilePendingOrders() {
        if (!properties.isConfigured() || !properties.isReconciliationEnabled()) {
            return;
        }

        Instant now = Instant.now();
        Date claimedAt = Date.from(now);
        Date createdAfter = Date.from(now.minus(Duration.ofDays(properties.getReconciliationMaxAgeDays())));
        Date createdBefore = Date.from(now.minus(Duration.ofMinutes(properties.getReconciliationMinAgeMinutes())));
        Date updatedBefore = Date.from(now.minusMillis(properties.getReconciliationDelayMs()));
        List<PayOrderSnapshot> pendingOrders = orderService.listPendingPayOrders(VNPAY_CHANNEL, createdAfter,
            createdBefore, updatedBefore, properties.getReconciliationBatchSize());

        for (PayOrderSnapshot order : pendingOrders) {
            if (!orderService.claimPendingPayOrder(order.id(), order.updateTime(), claimedAt)) {
                continue;
            }
            try {
                VnpayQueryResult queryResult = queryService.query(order);
                metrics.recordPayment(PaymentProvider.VNPAY, PaymentOperation.QUERY,
                    queryOutcome(queryResult.status()));
                PayOrderUpdateResult updateResult = switch (queryResult.status()) {
                    case SUCCESS -> orderService.processPayOrder(order.outTradeNo(), queryResult.tradeNo(),
                        VNPAY_CHANNEL, order.totalAmount(), true);
                    case FAILED -> orderService.processPayOrder(order.outTradeNo(), queryResult.tradeNo(),
                        VNPAY_CHANNEL, order.totalAmount(), false);
                    case PENDING, UNAVAILABLE -> null;
                };
                if (updateResult != null) {
                    log.info("Đối soát VNPAY đơn {}: query={}, update={}", order.outTradeNo(), queryResult.status(),
                        updateResult);
                }
            } catch (RuntimeException exception) {
                metrics.recordPayment(PaymentProvider.VNPAY, PaymentOperation.QUERY, Outcome.FAILED);
                log.error("Đối soát VNPAY thất bại cho đơn {}", order.outTradeNo(), exception);
            }
        }
    }

    private Outcome queryOutcome(VnpayQueryResult.Status status) {
        return switch (status) {
            case SUCCESS -> Outcome.SUCCESS;
            case FAILED -> Outcome.FAILED;
            case PENDING -> Outcome.PENDING;
            case UNAVAILABLE -> Outcome.UNAVAILABLE;
        };
    }
}
