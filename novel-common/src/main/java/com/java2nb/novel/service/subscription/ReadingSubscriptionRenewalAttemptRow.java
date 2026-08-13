package com.java2nb.novel.service.subscription;

import lombok.Data;

import java.util.Date;

@Data
public class ReadingSubscriptionRenewalAttemptRow {
    private Long id;
    private Long cycleId;
    private Integer attemptNo;
    private String fundingSource;
    private String providerRequestId;
    private String providerTransactionId;
    private String status;
    private String responseCode;
    private String responseMessage;
    private Date startedAt;
    private Date completedAt;
    private Long version;
}
