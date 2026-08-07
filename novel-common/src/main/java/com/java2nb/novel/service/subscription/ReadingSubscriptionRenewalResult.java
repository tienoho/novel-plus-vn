package com.java2nb.novel.service.subscription;

public enum ReadingSubscriptionRenewalResult {
    NOT_DUE,
    CYCLE_CREATED,
    PRICE_CONSENT_REQUIRED,
    SETTLED,
    PROVIDER_CLAIMED,
    PROVIDER_PENDING,
    RETRY_SCHEDULED,
    GRACE_EXPIRED
}
