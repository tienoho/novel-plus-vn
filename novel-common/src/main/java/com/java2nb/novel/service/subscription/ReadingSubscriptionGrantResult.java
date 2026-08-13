package com.java2nb.novel.service.subscription;

import java.util.Date;

public record ReadingSubscriptionGrantResult(ReadingSubscriptionGrantStatus status,
                                             long subscriptionId, Date periodStart,
                                             Date periodEnd, long ticketAmount) {
    public ReadingSubscriptionGrantResult {
        periodStart = periodStart == null ? null : new Date(periodStart.getTime());
        periodEnd = periodEnd == null ? null : new Date(periodEnd.getTime());
    }
    @Override public Date periodStart() { return periodStart == null ? null : new Date(periodStart.getTime()); }
    @Override public Date periodEnd() { return periodEnd == null ? null : new Date(periodEnd.getTime()); }
    public static ReadingSubscriptionGrantResult notDue(long id) {
        return new ReadingSubscriptionGrantResult(ReadingSubscriptionGrantStatus.NOT_DUE, id, null, null, 0);
    }
}
