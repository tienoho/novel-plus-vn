package com.java2nb.novel.dto.subscription;

import com.java2nb.novel.service.subscription.ReadingSubscriptionRow;

import java.util.Date;

public record ReadingSubscriptionResponse(long id, String planCode, long ticketsPerPeriod,
                                          int periodMonths, int ticketValidityDays,
                                          Date startAt, Date currentPeriodStart,
                                          Date currentPeriodEnd, Date nextRenewalAt,
                                          Date nextGrantAt, Date endAt, String status,
                                          boolean autoRenew, String primaryFundingSource,
                                          String fallbackFundingSource, long acceptedPlanVersion,
                                          long planVersion, Long priceVnd, Long priceXu,
                                          long version) {
    public static ReadingSubscriptionResponse from(ReadingSubscriptionRow row) {
        if (row == null) {
            return null;
        }
        return new ReadingSubscriptionResponse(row.getId(), row.getPlanCodeSnapshot(),
            row.getTicketsPerPeriodSnapshot(), row.getPeriodMonthsSnapshot(),
            row.getTicketValidityDaysSnapshot(), row.getStartAt(), row.getCurrentPeriodStart(),
            row.getCurrentPeriodEnd(), row.getNextRenewalAt(), row.getNextGrantAt(),
            row.getEndAt(), row.getStatus(), Boolean.TRUE.equals(row.getAutoRenew()),
            row.getPrimaryFundingSource(), row.getFallbackFundingSource(),
            row.getAcceptedPlanVersion(), row.getPlanVersionSnapshot(),
            row.getPriceVndSnapshot(), row.getPriceXuSnapshot(), row.getVersion());
    }
}
