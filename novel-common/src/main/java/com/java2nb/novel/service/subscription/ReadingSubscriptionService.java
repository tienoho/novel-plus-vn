package com.java2nb.novel.service.subscription;

import java.time.ZoneId;
import java.util.Date;
import java.util.List;

public interface ReadingSubscriptionService {
    ReadingSubscriptionRow activate(ReadingSubscriptionActivationCommand command);
    ReadingSubscriptionRow activatePurchase(ReadingSubscriptionPurchaseActivationCommand command);
    ReadingSubscriptionGrantResult grantDuePeriod(long subscriptionId, Date now,
                                                   ZoneId zoneId, String policyVersion);
    List<ReadingSubscriptionPlanRow> listActivePlans();
    List<ReadingSubscriptionPlanRow> listPlansForAdmin();
    ReadingSubscriptionPlanRow createPlan(ReadingSubscriptionPlanCommand command);
    ReadingSubscriptionPlanRow updatePlan(long planId, long expectedVersion,
                                          ReadingSubscriptionPlanCommand command);
    ReadingSubscriptionPlanRow changePlanStatus(long planId, long expectedVersion, String status);
    ReadingSubscriptionRow getCurrentSubscription(long userId);
    ReadingSubscriptionRow updateRenewalSettings(long userId, long subscriptionId,
                                                  long expectedVersion,
                                                  ReadingSubscriptionCheckoutOptions options);
    ReadingSubscriptionRow consentPrice(long userId, long subscriptionId,
                                        long expectedVersion, long acceptedPlanVersion,
                                        String clientRequestId);
    ReadingSubscriptionRow cancelAtPeriodEnd(long userId, long subscriptionId,
                                              long expectedVersion);
    List<ReadingSubscriptionMandateQueueItem> listMandateQueue(String status, int limit);
    List<ReadingSubscriptionMandateAdminAuditRow> listMandateAdminAudits(
        long mandateId, int limit);
    ReadingSubscriptionMandateAdminResult adminScheduleMandateRevocationRetry(
        long mandateId, long expectedVersion, long operatorId, String reason, Date now);
    List<ReadingSubscriptionPeriodGrantRow> listPeriodGrants(long userId, long subscriptionId,
                                                             int limit);
    ReadingSubscriptionPurchasePage listPurchaseReviews(String status, int page, int pageSize);
    ReadingSubscriptionPurchaseReviewResult retryPurchaseActivation(
        long purchaseId, long expectedVersion, long operatorId, String reason);
    ReadingSubscriptionPurchaseReviewResult sendPurchaseToRefund(
        long purchaseId, long expectedVersion, long operatorId, String reason);
}
