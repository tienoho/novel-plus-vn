package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.observability.NovelBusinessMetrics;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.Outcome;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.PaymentOperation;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.PaymentProvider;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.RenewalQueue;
import com.java2nb.novel.service.VnpayRecurringRevocationProcessor;
import com.java2nb.novel.service.VnpayRecurringRevocationResult;
import java.util.Date;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class VnpayRecurringRevocationSchedule {
    private final VnpayRecurringRevocationProcessor processor;
    private final NovelBusinessMetrics metrics;

    @Scheduled(fixedDelayString = "${vnpay.recurring.revocation-delay-ms:60000}")
    public void revokeDueMandates() {
        Date now = new Date();
        for (Long mandateId : processor.listDueIds(now)) {
            try {
                VnpayRecurringRevocationResult result = processor.process(mandateId, now);
                metrics.recordPayment(PaymentProvider.VNPAY_RECURRING, PaymentOperation.CANCEL,
                    switch (result) {
                        case REVOKED -> Outcome.SUCCESS;
                        case RETRY_SCHEDULED -> Outcome.PENDING;
                        case NOT_DUE -> Outcome.ALREADY_PROCESSED;
                    });
            } catch (RuntimeException exception) {
                metrics.recordPayment(PaymentProvider.VNPAY_RECURRING,
                    PaymentOperation.CANCEL, Outcome.FAILED);
                log.error("VNPAY-RECURRING-CANCEL-001 không thể thu hồi mandate: mandateId={}",
                    mandateId, exception);
            }
        }
        try {
            metrics.setRenewalQueue(RenewalQueue.MANDATE_REVOKE_PENDING,
                processor.countPending());
        } catch (RuntimeException exception) {
            log.error("VNPAY-RECURRING-CANCEL-002 không thể cập nhật metric hàng đợi mandate",
                exception);
        }
    }
}
