package com.java2nb.novel.dto.subscription;

import com.java2nb.novel.service.subscription.ReadingSubscriptionPlanRow;

public record ReadingSubscriptionPlanResponse(String planCode, String planName,
                                              long priceVnd,
                                              long ticketsPerPeriod, int periodMonths,
                                              int ticketValidityDays) {
    public static ReadingSubscriptionPlanResponse from(ReadingSubscriptionPlanRow row) {
        return new ReadingSubscriptionPlanResponse(row.getPlanCode(), row.getPlanName(),
            row.getPriceVnd() == null ? 0 : row.getPriceVnd(), row.getTicketsPerPeriod(),
            row.getPeriodMonths(), row.getTicketValidityDays());
    }
}
