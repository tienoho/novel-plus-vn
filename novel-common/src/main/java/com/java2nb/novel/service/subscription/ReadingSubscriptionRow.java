package com.java2nb.novel.service.subscription;

import lombok.Data;

import java.util.Date;

@Data
public class ReadingSubscriptionRow {
    private Long id;
    private Long userId;
    private Long planId;
    private String planCodeSnapshot;
    private Long ticketsPerPeriodSnapshot;
    private Integer periodMonthsSnapshot;
    private Integer ticketValidityDaysSnapshot;
    private Date startAt;
    private Date nextGrantAt;
    private Date endAt;
    private String status;
    private String sourceType;
    private String sourceRef;
    private String policyVersion;
    private Long version;
}
