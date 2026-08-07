package com.java2nb.novel.service.subscription;

import lombok.Data;

import java.util.Date;

@Data
public class ReadingSubscriptionPurchaseRow {
    private Long id;
    private Long outTradeNo;
    private Long userId;
    private Long planId;
    private String planCodeSnapshot;
    private String planNameSnapshot;
    private Long planVersionSnapshot;
    private Long priceVndSnapshot;
    private Long priceXuSnapshot;
    private Boolean autoRenew;
    private String primaryFundingSource;
    private String fallbackFundingSource;
    private Long acceptedPlanVersion;
    private Long ticketsPerPeriodSnapshot;
    private Integer periodMonthsSnapshot;
    private Integer ticketValidityDaysSnapshot;
    private Byte payChannel;
    private String clientRequestId;
    private String requestHash;
    private String policyVersion;
    private String zoneId;
    private String status;
    private Long subscriptionId;
    private Date settledAt;
    private Long version;
    private Date createTime;
    private Date updateTime;
}
