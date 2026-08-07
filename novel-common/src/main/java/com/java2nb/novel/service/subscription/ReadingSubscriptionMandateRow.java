package com.java2nb.novel.service.subscription;

import java.util.Date;
import lombok.Data;

@Data
public class ReadingSubscriptionMandateRow {
    private Long id;
    private Long userId;
    private String provider;
    private String merchantReference;
    private String providerRecurringId;
    private String providerTokenCiphertext;
    private Date tokenExpireAt;
    private String status;
    private Date consentedAt;
    private Date revokedAt;
    private Long version;
}
