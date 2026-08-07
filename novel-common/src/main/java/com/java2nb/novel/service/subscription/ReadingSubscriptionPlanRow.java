package com.java2nb.novel.service.subscription;

import lombok.Data;

import java.util.Date;

@Data
public class ReadingSubscriptionPlanRow {
    private Long id;
    private String planCode;
    private String planName;
    private Long planVersion;
    private Long priceVnd;
    private Long priceXu;
    private Date priceAnnouncedAt;
    private Date priceEffectiveAt;
    private Long ticketsPerPeriod;
    private Integer periodMonths;
    private Integer ticketValidityDays;
    private String status;
    private Long version;
}
