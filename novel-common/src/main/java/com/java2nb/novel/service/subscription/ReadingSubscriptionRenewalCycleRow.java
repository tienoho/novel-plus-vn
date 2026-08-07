package com.java2nb.novel.service.subscription;

import java.util.Date;
import lombok.Data;

@Data
public class ReadingSubscriptionRenewalCycleRow {
    private Long id;
    private Long subscriptionId;
    private Long userId;
    private Date periodStart;
    private Date periodEnd;
    private Date graceEndAt;
    private Long planVersionSnapshot;
    private Long priceVndSnapshot;
    private Long priceXuSnapshot;
    private String status;
    private Integer attemptCount;
    private Date nextAttemptAt;
    private String settledSource;
    private String settledReference;
    private Date settledAt;
    private String idempotencyKey;
    private Long version;
}
