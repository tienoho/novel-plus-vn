package com.java2nb.novel.service.subscription;

import java.util.Date;
import lombok.Data;

@Data
public class ReadingSubscriptionMandateRow {
    private Long id;
    private Long userId;
    private String provider;
    private String merchantReference;
    private String clientRequestId;
    private String requestHash;
    private String planCodeSnapshot;
    private Long acceptedPlanVersion;
    private String providerRecurringId;
    private String providerDataKeyCiphertext;
    private String providerTokenCiphertext;
    private Date tokenExpireAt;
    private String status;
    private Date consentedAt;
    private Date revokedAt;
    private Date revokeRequestedAt;
    private Date revokeNextAttemptAt;
    private Date revokeLeaseUntil;
    private Integer revokeAttemptCount;
    private String revokeLastError;
    private Long version;
}
