package com.java2nb.novel.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.ZoneId;

@Data
@Component
@ConfigurationProperties(prefix = "novel.reader-entitlement")
public class ReaderEntitlementProperties {
    private boolean enabled;
    private String policyVersion = "v1";
    private int maxLotsPerSpend = 20;
    private String expiryCron = "0 30 3 * * ?";
    private int expiryBatchSize = 500;
    private int expiryMaxLotsPerUser = 1000;
    private String subscriptionGrantCron = "0 10 0 * * ?";
    private String subscriptionZoneId = "Asia/Ho_Chi_Minh";
    private int subscriptionGrantBatchSize = 200;
    private boolean subscriptionRenewalEnabled;
    private long subscriptionRenewalDelayMs = 60_000;
    private int subscriptionRenewalBatchSize = 100;

    public boolean isConfigured() {
        return policyVersion != null && !policyVersion.isBlank()
            && policyVersion.length() <= 32
            && maxLotsPerSpend > 0 && maxLotsPerSpend <= 100
            && expiryCron != null && !expiryCron.isBlank()
            && expiryBatchSize > 0 && expiryBatchSize <= 10_000
            && expiryMaxLotsPerUser > 0 && expiryMaxLotsPerUser <= 10_000
            && subscriptionGrantCron != null && !subscriptionGrantCron.isBlank()
            && isValidZoneId(subscriptionZoneId)
            && subscriptionGrantBatchSize > 0 && subscriptionGrantBatchSize <= 10_000
            && subscriptionRenewalDelayMs >= 10_000 && subscriptionRenewalDelayMs <= 3_600_000
            && subscriptionRenewalBatchSize > 0 && subscriptionRenewalBatchSize <= 500;
    }

    private boolean isValidZoneId(String zoneId) {
        if (zoneId == null || zoneId.isBlank()) {
            return false;
        }
        try {
            ZoneId.of(zoneId);
            return true;
        } catch (DateTimeException exception) {
            return false;
        }
    }
}
