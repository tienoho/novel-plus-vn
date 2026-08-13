package com.java2nb.novel.service.entitlement;

import java.util.Date;

public record ReadingTicketUnlockCommand(long userId, long bookId, long bookIndexId,
                                         String clientRequestId, Date occurredAt,
                                         String policyVersion, int maxLotsPerSpend) {
    public ReadingTicketUnlockCommand {
        clientRequestId = clientRequestId == null ? "" : clientRequestId.trim();
        policyVersion = policyVersion == null ? "" : policyVersion.trim();
        if (userId <= 0 || bookId <= 0 || bookIndexId <= 0
            || !clientRequestId.matches("[A-Za-z0-9_-]{8,64}")
            || occurredAt == null || policyVersion.isEmpty() || policyVersion.length() > 32
            || maxLotsPerSpend <= 0 || maxLotsPerSpend > 100) {
            throw new IllegalArgumentException("Yêu cầu mở chương bằng Vé đọc không hợp lệ");
        }
        occurredAt = new Date(occurredAt.getTime());
    }

    @Override public Date occurredAt() { return new Date(occurredAt.getTime()); }
}
