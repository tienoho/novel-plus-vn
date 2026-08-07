package com.java2nb.novel.service.subscription;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;

public interface ReadingSubscriptionRenewalService {
    List<Long> listDueSubscriptionIds(Date now, int limit);
    ReadingSubscriptionRenewalResult prepareCycle(long subscriptionId, Date now, ZoneId zoneId);
    List<Long> listDueCycleIds(Date now, int limit);
    long countCyclesByStatus(String status);
    long countPastDueSubscriptions();
    ReadingSubscriptionRenewalResult processCycle(long cycleId, Date now);
    ReadingSubscriptionRenewalResult recordWalletFailure(long cycleId, Date now);
    ReadingSubscriptionProviderCharge getProviderCharge(long cycleId);
    ReadingSubscriptionRenewalResult settleProviderCharge(long cycleId, int attemptNo,
                                                           String providerTransactionId, Date now);
    ReadingSubscriptionRenewalResult recordProviderFailure(long cycleId, int attemptNo,
                                                            String responseCode, Date now);
    ReadingSubscriptionRenewalResult recordProviderPending(long cycleId, int attemptNo,
                                                            String responseCode, Date now);
    List<ReadingSubscriptionRenewalQueueItem> listRenewalQueue(String status, int limit);
    List<ReadingSubscriptionRenewalAttemptRow> listRenewalAttempts(long cycleId, int limit);
    List<ReadingSubscriptionRenewalAdminAuditRow> listRenewalAdminAudits(long cycleId, int limit);
    ReadingSubscriptionRenewalResult adminScheduleRetry(long cycleId, long expectedVersion,
                                                        long operatorId, String reason, Date now);
}
