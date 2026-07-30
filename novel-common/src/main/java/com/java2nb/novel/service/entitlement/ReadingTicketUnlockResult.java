package com.java2nb.novel.service.entitlement;

public record ReadingTicketUnlockResult(ReadingTicketPostResult status, long entitlementId,
                                        long availableBalance) {
}
