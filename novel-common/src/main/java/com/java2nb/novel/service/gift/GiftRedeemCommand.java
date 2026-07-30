package com.java2nb.novel.service.gift;

import java.util.Date;
import java.util.Locale;

public record GiftRedeemCommand(long userId, String code, String clientRequestId, Date occurredAt) {
    public GiftRedeemCommand {
        code = code == null ? "" : code.replace("-", "").replace(" ", "")
            .toUpperCase(Locale.ROOT);
        clientRequestId = clientRequestId == null ? "" : clientRequestId.trim();
        if (userId <= 0 || !code.matches("[A-Z0-9]{16,64}")
            || !clientRequestId.matches("[A-Za-z0-9_-]{8,64}") || occurredAt == null) {
            throw new IllegalArgumentException("Yêu cầu đổi mã quà không hợp lệ");
        }
        occurredAt = new Date(occurredAt.getTime());
    }

    @Override public Date occurredAt() { return new Date(occurredAt.getTime()); }
}
