package com.java2nb.novel.service.subscription;

import java.util.Date;

public record ReadingSubscriptionProviderQuery(long cycleId,
                                               int attemptNo,
                                               String providerRequestId,
                                               long amountVnd,
                                               Date merchantDate) {
}
