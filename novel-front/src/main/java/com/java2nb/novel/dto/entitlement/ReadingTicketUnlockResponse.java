package com.java2nb.novel.dto.entitlement;

import com.java2nb.novel.service.entitlement.ReadingTicketUnlockResult;

public record ReadingTicketUnlockResponse(String status, Long entitlementId,
                                          long availableBalance) {
    public static ReadingTicketUnlockResponse from(ReadingTicketUnlockResult result) {
        return new ReadingTicketUnlockResponse(result.status().name(), result.entitlementId(),
            result.availableBalance());
    }

    public static ReadingTicketUnlockResponse alreadyAccessible(long availableBalance) {
        return new ReadingTicketUnlockResponse("ALREADY_ACCESSIBLE", null, availableBalance);
    }
}
