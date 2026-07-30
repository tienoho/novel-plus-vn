package com.java2nb.novel.service.entitlement;

import java.util.Date;

public record ReadingTicketGrantCommand(long userId, long amount, String sourceType,
                                        String sourceRef, String idempotencyKey,
                                        Date effectiveAt, Date expireAt, String operatorType,
                                        Long operatorId, String reason, String policyVersion) {
    public ReadingTicketGrantCommand {
        if (userId <= 0 || amount <= 0 || amount > 100_000) {
            throw new IllegalArgumentException("Người dùng hoặc số Vé đọc không hợp lệ");
        }
        sourceType = required(sourceType, 32, "nguồn cấp");
        sourceRef = required(sourceRef, 96, "tham chiếu nguồn");
        idempotencyKey = required(idempotencyKey, 128, "khóa idempotency");
        operatorType = required(operatorType, 16, "loại người thực hiện");
        policyVersion = required(policyVersion, 32, "phiên bản chính sách");
        if (effectiveAt == null || expireAt == null || !expireAt.after(effectiveAt)) {
            throw new IllegalArgumentException("Thời hạn lô Vé đọc không hợp lệ");
        }
        effectiveAt = new Date(effectiveAt.getTime());
        expireAt = new Date(expireAt.getTime());
        reason = reason == null ? null : reason.trim();
    }

    @Override public Date effectiveAt() { return new Date(effectiveAt.getTime()); }
    @Override public Date expireAt() { return new Date(expireAt.getTime()); }

    private static String required(String value, int max, String field) {
        String normalized = value == null ? "" : value.trim();
        if (normalized.isEmpty() || normalized.length() > max) {
            throw new IllegalArgumentException("Thiếu hoặc sai " + field + " Vé đọc");
        }
        return normalized;
    }
}
