package com.java2nb.novel.service;

import com.java2nb.novel.core.config.VnpayRecurringProperties;
import com.java2nb.novel.mapper.ReadingSubscriptionMandateMapper;
import com.java2nb.novel.service.finance.PiiCryptoService;
import com.java2nb.novel.service.subscription.ReadingSubscriptionMandateRow;
import java.security.SecureRandom;
import java.util.Date;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class VnpayRecurringRevocationProcessor {
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String USER_AGENT = "KhoiThuServer";

    private final VnpayRecurringProperties properties;
    private final ReadingSubscriptionMandateMapper mandateMapper;
    private final PiiCryptoService piiCryptoService;
    private final VnpayRecurringClient client;

    public List<Long> listDueIds(Date now) {
        if (now == null || !properties.isConfigured() || !piiCryptoService.isConfigured()) {
            return List.of();
        }
        return mandateMapper.selectDueRevocationIds(now, properties.getRevocationBatchSize());
    }

    public VnpayRecurringRevocationResult process(long mandateId, Date now) {
        if (mandateId <= 0 || now == null || !properties.isConfigured()
            || !piiCryptoService.isConfigured()) {
            return VnpayRecurringRevocationResult.NOT_DUE;
        }
        Date leaseUntil = new Date(Math.addExact(now.getTime(), properties.getRevocationLeaseMs()));
        if (mandateMapper.claimRevocation(mandateId, now, leaseUntil) != 1) {
            return VnpayRecurringRevocationResult.NOT_DUE;
        }
        ReadingSubscriptionMandateRow mandate = mandateMapper.selectById(mandateId);
        if (mandate == null || !"REVOKE_PENDING".equals(mandate.getStatus())) {
            return VnpayRecurringRevocationResult.NOT_DUE;
        }

        VnpayRecurringCancelResult result;
        try {
            String token = piiCryptoService.decrypt(mandate.getProviderTokenCiphertext());
            if (token == null || token.isBlank() || mandate.getProviderRecurringId() == null
                || mandate.getProviderRecurringId().isBlank()) {
                result = VnpayRecurringCancelResult.retry("MISSING_PROVIDER_REFERENCE");
            } else {
                result = client.cancel(new VnpayRecurringCancelCommand(requestId(),
                    mandate.getProviderRecurringId(), token, properties.getServerIp(), USER_AGENT));
            }
        } catch (RuntimeException exception) {
            result = VnpayRecurringCancelResult.retry("TOKEN_DECRYPT_FAILED");
        }

        if (result.status() == VnpayRecurringCancelResult.Status.REVOKED) {
            return mandateMapper.markRevoked(mandateId, mandate.getVersion(), now) == 1
                ? VnpayRecurringRevocationResult.REVOKED
                : VnpayRecurringRevocationResult.NOT_DUE;
        }
        Date nextAttemptAt = new Date(Math.addExact(now.getTime(),
            properties.getRevocationRetryDelayMs()));
        String responseCode = normalizeResponseCode(result.responseCode());
        return mandateMapper.scheduleRevocationRetry(mandateId, mandate.getVersion(),
            nextAttemptAt, responseCode) == 1
            ? VnpayRecurringRevocationResult.RETRY_SCHEDULED
            : VnpayRecurringRevocationResult.NOT_DUE;
    }

    public long countPending() {
        return mandateMapper.countPendingRevocations();
    }

    private String requestId() {
        return System.currentTimeMillis() + String.format("%04d", RANDOM.nextInt(10_000));
    }

    private String normalizeResponseCode(String value) {
        String normalized = value == null || value.isBlank() ? "UNKNOWN" : value.trim();
        return normalized.substring(0, Math.min(normalized.length(), 128));
    }
}
