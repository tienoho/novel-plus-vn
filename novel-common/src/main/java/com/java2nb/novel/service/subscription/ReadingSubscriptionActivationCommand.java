package com.java2nb.novel.service.subscription;

import java.util.Date;

public record ReadingSubscriptionActivationCommand(long userId, String planCode, Date startAt,
                                                    Date endAt, String sourceType,
                                                    String sourceRef, String policyVersion) {
    public ReadingSubscriptionActivationCommand {
        planCode = required(planCode, 32);
        sourceType = required(sourceType, 32);
        sourceRef = required(sourceRef, 128);
        policyVersion = required(policyVersion, 32);
        if (userId <= 0 || startAt == null || (endAt != null && !endAt.after(startAt))) {
            throw new IllegalArgumentException("Yêu cầu kích hoạt thuê bao không hợp lệ");
        }
        startAt = new Date(startAt.getTime());
        endAt = endAt == null ? null : new Date(endAt.getTime());
    }

    @Override public Date startAt() { return new Date(startAt.getTime()); }
    @Override public Date endAt() { return endAt == null ? null : new Date(endAt.getTime()); }

    private static String required(String value, int max) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException("Yêu cầu kích hoạt thuê bao không hợp lệ");
        }
        return normalized;
    }
}
