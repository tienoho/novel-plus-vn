package com.java2nb.novel.service.subscription;

import java.util.Date;
import lombok.Data;

@Data
public class ReadingSubscriptionMandateQueueItem {
    private Long mandateId;
    private Long userId;
    private String merchantReference;
    private String providerRecurringId;
    private String status;
    private Date revokeRequestedAt;
    private Date revokeNextAttemptAt;
    private Date revokeLeaseUntil;
    private Integer revokeAttemptCount;
    private String revokeLastError;
    private Date updateTime;
    private Long version;
}
