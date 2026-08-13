package com.java2nb.novel.service.gamification;

import java.util.Date;

public record TicketRiskCommand(
    long userId,
    long seasonId,
    long bookId,
    String clientRequestId,
    String deviceHash,
    String ipHash,
    Date occurredAt,
    String policyVersion
) {
    public TicketRiskCommand {
        if (userId <= 0 || seasonId <= 0 || bookId <= 0 || occurredAt == null
            || clientRequestId == null || clientRequestId.isBlank() || clientRequestId.length() > 64
            || deviceHash == null || !deviceHash.matches("[0-9a-f]{64}")
            || ipHash == null || !ipHash.matches("[0-9a-f]{64}")
            || policyVersion == null || policyVersion.isBlank() || policyVersion.length() > 32) {
            throw new IllegalArgumentException("Dữ liệu đánh giá rủi ro thắp Đuốc không hợp lệ");
        }
        occurredAt = new Date(occurredAt.getTime());
    }

    @Override
    public Date occurredAt() {
        return new Date(occurredAt.getTime());
    }
}
