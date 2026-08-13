package com.java2nb.novel.service.subscription;

import java.util.Date;

public record ReadingSubscriptionPurchaseActivationCommand(
    long userId, long planId, String planCode, long ticketsPerPeriod,
    int periodMonths, int ticketValidityDays, Date startAt, Date endAt,
    String sourceRef, String policyVersion, long planVersion, Long priceVnd,
    Long priceXu, boolean autoRenew, String primaryFundingSource,
    String fallbackFundingSource, long acceptedPlanVersion) {
    public ReadingSubscriptionPurchaseActivationCommand(
        long userId, long planId, String planCode, long ticketsPerPeriod,
        int periodMonths, int ticketValidityDays, Date startAt, Date endAt,
        String sourceRef, String policyVersion) {
        this(userId, planId, planCode, ticketsPerPeriod, periodMonths, ticketValidityDays,
            startAt, endAt, sourceRef, policyVersion, 1, null, null, false, null, null, 1);
    }

    public ReadingSubscriptionPurchaseActivationCommand {
        if (userId <= 0 || planId <= 0 || planCode == null || planCode.isBlank()
            || planCode.length() > 32 || ticketsPerPeriod <= 0 || periodMonths <= 0
            || ticketValidityDays <= 0 || startAt == null || endAt == null
            || !endAt.after(startAt) || sourceRef == null || sourceRef.isBlank()
            || sourceRef.length() > 128 || policyVersion == null || policyVersion.isBlank()
            || policyVersion.length() > 32 || planVersion < 1 || acceptedPlanVersion < 1
            || (autoRenew && (primaryFundingSource == null || primaryFundingSource.isBlank()))
            || !validFundingSource(primaryFundingSource)
            || !validFundingSource(fallbackFundingSource)
            || (fallbackFundingSource != null && fallbackFundingSource.equals(primaryFundingSource))) {
            throw new IllegalArgumentException("Yêu cầu kích hoạt thuê bao đã mua không hợp lệ");
        }
        startAt = new Date(startAt.getTime());
        endAt = new Date(endAt.getTime());
    }

    @Override public Date startAt() { return new Date(startAt.getTime()); }
    @Override public Date endAt() { return new Date(endAt.getTime()); }

    private static boolean validFundingSource(String value) {
        return value == null || "VNPAY_RECURRING".equals(value) || "WALLET_XU".equals(value);
    }
}
