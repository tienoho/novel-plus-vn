package com.java2nb.novel.service.subscription;

import lombok.Data;

import java.util.Date;

@Data
public class ReadingSubscriptionRenewalQueueItem {
    private Long cycleId;
    private Long subscriptionId;
    private Long userId;
    private String planCode;
    private String status;
    private Integer attemptCount;
    private Date nextAttemptAt;
    private Date graceEndAt;
    private Long priceVnd;
    private Long priceXu;
    private String fundingSource;
    private String attemptStatus;
    private String responseCode;
    private String providerTransactionId;
    private Date attemptUpdatedAt;
    private Long version;
}
