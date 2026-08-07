package com.java2nb.novel.service.subscription;

import java.util.Date;

public record ReadingSubscriptionProviderCharge(long cycleId,
                                                 long subscriptionId,
                                                 long userId,
                                                 int attemptNo,
                                                 long amountVnd,
                                                 Date periodStart,
                                                 String providerRequestId,
                                                 String providerRecurringId,
                                                 String providerTokenCiphertext,
                                                 Date tokenExpireAt) {
}
