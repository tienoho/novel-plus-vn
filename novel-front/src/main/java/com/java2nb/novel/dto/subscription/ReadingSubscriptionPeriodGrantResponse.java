package com.java2nb.novel.dto.subscription;

import com.java2nb.novel.service.subscription.ReadingSubscriptionPeriodGrantRow;

import java.util.Date;

public record ReadingSubscriptionPeriodGrantResponse(long id, long subscriptionId,
                                                      Date periodStart, Date periodEnd,
                                                      long ticketAmount, Date ticketExpireAt,
                                                      Date createTime) {
    public static ReadingSubscriptionPeriodGrantResponse from(
        ReadingSubscriptionPeriodGrantRow row) {
        return new ReadingSubscriptionPeriodGrantResponse(row.getId(), row.getSubscriptionId(),
            row.getPeriodStart(), row.getPeriodEnd(), row.getTicketAmount(),
            row.getTicketExpireAt(), row.getCreateTime());
    }
}
