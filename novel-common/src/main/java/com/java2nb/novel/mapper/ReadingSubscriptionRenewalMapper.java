package com.java2nb.novel.mapper;

import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalCycleRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionProviderCharge;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalAdminAuditRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalAttemptRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalQueueItem;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRow;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReadingSubscriptionRenewalMapper {
    List<Long> selectDueSubscriptionIds(@Param("now") Date now, @Param("limit") int limit);
    ReadingSubscriptionRow lockDueSubscription(@Param("subscriptionId") long subscriptionId,
                                               @Param("now") Date now);
    int markPriceConsentRequired(@Param("subscriptionId") long subscriptionId,
                                 @Param("expectedVersion") long expectedVersion,
                                 @Param("now") Date now);
    int insertCycle(@Param("subscription") ReadingSubscriptionRow subscription,
                    @Param("periodStart") Date periodStart,
                    @Param("periodEnd") Date periodEnd,
                    @Param("graceEndAt") Date graceEndAt,
                    @Param("idempotencyKey") String idempotencyKey,
                    @Param("now") Date now);
    List<Long> selectDueCycleIds(@Param("now") Date now, @Param("limit") int limit);
    long countCyclesByStatus(@Param("status") String status);
    long countPastDueSubscriptions();
    ReadingSubscriptionRenewalCycleRow lockCycle(@Param("cycleId") long cycleId);
    ReadingSubscriptionRow lockSubscription(@Param("subscriptionId") long subscriptionId);
    int insertAttempt(@Param("cycleId") long cycleId, @Param("attemptNo") int attemptNo,
                      @Param("fundingSource") String fundingSource,
                      @Param("status") String status, @Param("requestHash") String requestHash,
                      @Param("now") Date now);
    int insertProviderAttempt(@Param("cycleId") long cycleId,
                              @Param("attemptNo") int attemptNo,
                              @Param("providerRequestId") String providerRequestId,
                              @Param("requestHash") String requestHash,
                              @Param("now") Date now);
    int markProviderProcessing(@Param("cycleId") long cycleId,
                               @Param("expectedVersion") long expectedVersion,
                               @Param("attemptNo") int attemptNo,
                               @Param("now") Date now);
    ReadingSubscriptionProviderCharge selectProviderCharge(@Param("cycleId") long cycleId);
    int markAttemptSettled(@Param("cycleId") long cycleId, @Param("attemptNo") int attemptNo,
                           @Param("reference") String reference, @Param("now") Date now);
    int markCycleSettled(@Param("cycleId") long cycleId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("fundingSource") String fundingSource,
                         @Param("reference") String reference, @Param("now") Date now);
    int advanceSubscription(@Param("cycle") ReadingSubscriptionRenewalCycleRow cycle,
                            @Param("expectedVersion") long expectedVersion);
    int markProviderPending(@Param("cycleId") long cycleId,
                            @Param("expectedVersion") long expectedVersion,
                            @Param("attemptNo") int attemptNo, @Param("now") Date now);
    int markProviderAttemptUnknown(@Param("cycleId") long cycleId,
                                   @Param("attemptNo") int attemptNo,
                                   @Param("responseCode") String responseCode,
                                   @Param("now") Date now);
    int markProviderAttemptFailed(@Param("cycleId") long cycleId,
                                  @Param("attemptNo") int attemptNo,
                                  @Param("responseCode") String responseCode,
                                  @Param("now") Date now);
    int scheduleProviderRetry(@Param("cycleId") long cycleId,
                              @Param("expectedVersion") long expectedVersion,
                              @Param("nextAttemptAt") Date nextAttemptAt);
    int markProviderAttemptSettled(@Param("cycleId") long cycleId,
                                   @Param("attemptNo") int attemptNo,
                                   @Param("reference") String reference,
                                   @Param("now") Date now);
    int markProviderCycleSettled(@Param("cycleId") long cycleId,
                                 @Param("expectedVersion") long expectedVersion,
                                 @Param("reference") String reference,
                                 @Param("now") Date now);
    int markAttemptFailed(@Param("cycleId") long cycleId, @Param("attemptNo") int attemptNo,
                          @Param("message") String message, @Param("now") Date now);
    int scheduleRetry(@Param("cycleId") long cycleId,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("attemptCount") int attemptCount,
                      @Param("nextAttemptAt") Date nextAttemptAt);
    int markSubscriptionPastDue(@Param("subscriptionId") long subscriptionId,
                                @Param("expectedVersion") long expectedVersion);
    int expireCycle(@Param("cycleId") long cycleId,
                    @Param("expectedVersion") long expectedVersion);
    int expireSubscription(@Param("subscriptionId") long subscriptionId,
                           @Param("expectedVersion") long expectedVersion,
                           @Param("now") Date now);
    List<ReadingSubscriptionRenewalQueueItem> selectRenewalQueue(
        @Param("status") String status, @Param("limit") int limit);
    List<ReadingSubscriptionRenewalAttemptRow> selectRenewalAttempts(
        @Param("cycleId") long cycleId, @Param("limit") int limit);
    List<ReadingSubscriptionRenewalAdminAuditRow> selectRenewalAdminAudits(
        @Param("cycleId") long cycleId, @Param("limit") int limit);
    int adminScheduleRetry(@Param("cycleId") long cycleId,
                           @Param("expectedVersion") long expectedVersion,
                           @Param("now") Date now);
    int insertRenewalAdminAudit(@Param("cycleId") long cycleId,
                                @Param("userId") long userId,
                                @Param("operatorId") long operatorId,
                                @Param("action") String action,
                                @Param("beforeStatus") String beforeStatus,
                                @Param("afterStatus") String afterStatus,
                                @Param("reason") String reason,
                                @Param("now") Date now);
}
