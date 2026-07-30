package com.java2nb.novel.service.subscription;

import lombok.Data;

@Data
public class ReadingSubscriptionPlanRow {
    private Long id;
    private String planCode;
    private String planName;
    private Long priceVnd;
    private Long ticketsPerPeriod;
    private Integer periodMonths;
    private Integer ticketValidityDays;
    private String status;
    private Long version;
}
