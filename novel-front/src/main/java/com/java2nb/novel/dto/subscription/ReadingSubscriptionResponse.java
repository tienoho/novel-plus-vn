package com.java2nb.novel.dto.subscription;

import com.java2nb.novel.service.subscription.ReadingSubscriptionRow;

import java.util.Date;

public record ReadingSubscriptionResponse(long id, String planCode, long ticketsPerPeriod,
                                          int periodMonths, int ticketValidityDays,
                                          Date startAt, Date nextGrantAt, Date endAt,
                                          String status) {
    public static ReadingSubscriptionResponse from(ReadingSubscriptionRow row) {
        if (row == null) {
            return null;
        }
        return new ReadingSubscriptionResponse(row.getId(), row.getPlanCodeSnapshot(),
            row.getTicketsPerPeriodSnapshot(), row.getPeriodMonthsSnapshot(),
            row.getTicketValidityDaysSnapshot(), row.getStartAt(), row.getNextGrantAt(),
            row.getEndAt(), row.getStatus());
    }
}
