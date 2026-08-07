package com.java2nb.novel.service.subscription;

import java.util.Locale;

public record ReadingSubscriptionCheckoutOptions(
    boolean autoRenew,
    String primaryFundingSource,
    String fallbackFundingSource,
    long acceptedPlanVersion) {

    public ReadingSubscriptionCheckoutOptions {
        primaryFundingSource = normalize(primaryFundingSource);
        fallbackFundingSource = normalize(fallbackFundingSource);
        if (acceptedPlanVersion < 1
            || (autoRenew && primaryFundingSource == null)
            || (!autoRenew && (primaryFundingSource != null || fallbackFundingSource != null))
            || !valid(primaryFundingSource) || !valid(fallbackFundingSource)
            || (fallbackFundingSource != null && fallbackFundingSource.equals(primaryFundingSource))) {
            throw new IllegalArgumentException("Cấu hình tự gia hạn không hợp lệ");
        }
    }

    public static ReadingSubscriptionCheckoutOptions oneOff(long acceptedPlanVersion) {
        return new ReadingSubscriptionCheckoutOptions(false, null, null, acceptedPlanVersion);
    }

    private static String normalize(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim().toUpperCase(Locale.ROOT);
    }

    private static boolean valid(String value) {
        return value == null || "VNPAY_RECURRING".equals(value) || "WALLET_XU".equals(value);
    }
}
