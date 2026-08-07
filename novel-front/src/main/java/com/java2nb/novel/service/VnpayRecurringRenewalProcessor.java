package com.java2nb.novel.service;

import com.java2nb.novel.core.config.VnpayRecurringProperties;
import com.java2nb.novel.core.observability.NovelBusinessMetrics;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.Outcome;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.PaymentOperation;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.PaymentProvider;
import com.java2nb.novel.service.finance.PiiCryptoService;
import com.java2nb.novel.service.subscription.ReadingSubscriptionProviderCharge;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalResult;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalService;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

@Service
public class VnpayRecurringRenewalProcessor {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ReadingSubscriptionRenewalService renewalService;
    private final VnpayRecurringClient client;
    private final VnpayRecurringProperties properties;
    private final PiiCryptoService piiCryptoService;
    private final NovelBusinessMetrics metrics;

    public VnpayRecurringRenewalProcessor(ReadingSubscriptionRenewalService renewalService,
                                          VnpayRecurringClient client,
                                          VnpayRecurringProperties properties,
                                          PiiCryptoService piiCryptoService,
                                          NovelBusinessMetrics metrics) {
        this.renewalService = renewalService;
        this.client = client;
        this.properties = properties;
        this.piiCryptoService = piiCryptoService;
        this.metrics = metrics;
    }

    public ReadingSubscriptionRenewalResult process(long cycleId, Date now) {
        ReadingSubscriptionProviderCharge charge = renewalService.getProviderCharge(cycleId);
        if (charge == null) {
            throw new IllegalStateException("Không tìm thấy attempt VNPAY đã claim");
        }
        if (!properties.isConfigured() || !piiCryptoService.isConfigured()
            || charge.providerRecurringId() == null || charge.providerRecurringId().isBlank()
            || charge.providerTokenCiphertext() == null || charge.providerTokenCiphertext().isBlank()
            || charge.tokenExpireAt() != null && !charge.tokenExpireAt().after(now)) {
            metrics.recordPayment(PaymentProvider.VNPAY_RECURRING, PaymentOperation.RENEWAL,
                Outcome.UNAVAILABLE);
            return renewalService.recordProviderFailure(cycleId, charge.attemptNo(),
                "MANDATE_UNAVAILABLE", now);
        }
        String token;
        try {
            token = piiCryptoService.decrypt(charge.providerTokenCiphertext());
        } catch (RuntimeException exception) {
            metrics.recordPayment(PaymentProvider.VNPAY_RECURRING, PaymentOperation.RENEWAL,
                Outcome.FAILED);
            return renewalService.recordProviderFailure(cycleId, charge.attemptNo(),
                "TOKEN_DECRYPT_FAILED", now);
        }
        ZoneId zoneId = ZoneId.of(properties.getTimeZone());
        VnpayRecurringChargeCommand command = new VnpayRecurringChargeCommand(
            requestId(), charge.providerRequestId(), charge.providerRecurringId(), token,
            charge.amountVnd(), charge.periodStart().toInstant().atZone(zoneId).toLocalDate(),
            LocalDateTime.ofInstant(now.toInstant(), zoneId));
        VnpayRecurringChargeResult result = client.charge(command);
        metrics.recordPayment(PaymentProvider.VNPAY_RECURRING, PaymentOperation.RENEWAL,
            switch (result.status()) {
                case SETTLED -> Outcome.SUCCESS;
                case FINAL_FAILURE -> Outcome.FAILED;
                case PENDING -> Outcome.PENDING;
            });
        return switch (result.status()) {
            case SETTLED -> renewalService.settleProviderCharge(cycleId, charge.attemptNo(),
                result.providerTransactionId(), now);
            case FINAL_FAILURE -> renewalService.recordProviderFailure(cycleId,
                charge.attemptNo(), result.responseCode(), now);
            case PENDING -> renewalService.recordProviderPending(cycleId,
                charge.attemptNo(), result.responseCode(), now);
        };
    }

    private String requestId() {
        return String.valueOf(System.currentTimeMillis()) + String.format("%04d", RANDOM.nextInt(10_000));
    }
}
