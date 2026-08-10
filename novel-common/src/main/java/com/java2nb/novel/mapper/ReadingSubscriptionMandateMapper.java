package com.java2nb.novel.mapper;

import com.java2nb.novel.service.subscription.ReadingSubscriptionMandateRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionMandateQueueItem;
import com.java2nb.novel.service.subscription.ReadingSubscriptionMandateAdminAuditRow;
import java.util.Date;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface ReadingSubscriptionMandateMapper {
    int insertPending(@Param("userId") long userId,
                      @Param("merchantReference") String merchantReference,
                      @Param("clientRequestId") String clientRequestId,
                      @Param("requestHash") String requestHash,
                      @Param("planCodeSnapshot") String planCodeSnapshot,
                      @Param("acceptedPlanVersion") long acceptedPlanVersion);
    ReadingSubscriptionMandateRow selectByClientRequestId(
        @Param("userId") long userId, @Param("clientRequestId") String clientRequestId);
    ReadingSubscriptionMandateRow selectByMerchantReference(
        @Param("merchantReference") String merchantReference);
    ReadingSubscriptionMandateRow selectByMerchantReferenceForUpdate(
        @Param("merchantReference") String merchantReference);
    ReadingSubscriptionMandateRow selectActiveByUser(@Param("userId") long userId);
    ReadingSubscriptionMandateRow selectOpenByUser(@Param("userId") long userId);
    int registerProviderInitialization(@Param("merchantReference") String merchantReference,
                                       @Param("providerRecurringId") String providerRecurringId,
                                       @Param("providerDataKeyCiphertext") String providerDataKeyCiphertext);
    int activate(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
                 @Param("providerTokenCiphertext") String providerTokenCiphertext,
                 @Param("tokenExpireAt") Date tokenExpireAt,
                 @Param("consentedAt") Date consentedAt);
    int fail(@Param("id") long id, @Param("expectedVersion") long expectedVersion);
    int requestRevocation(@Param("userId") long userId, @Param("now") Date now);
    List<Long> selectDueRevocationIds(@Param("now") Date now, @Param("limit") int limit);
    int claimRevocation(@Param("id") long id, @Param("now") Date now,
                        @Param("leaseUntil") Date leaseUntil);
    ReadingSubscriptionMandateRow selectById(@Param("id") long id);
    int markRevoked(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
                    @Param("revokedAt") Date revokedAt);
    int scheduleRevocationRetry(@Param("id") long id,
                                @Param("expectedVersion") long expectedVersion,
                                @Param("nextAttemptAt") Date nextAttemptAt,
                                @Param("lastError") String lastError);
    List<ReadingSubscriptionMandateQueueItem> selectMandateQueue(
        @Param("status") String status, @Param("limit") int limit);
    ReadingSubscriptionMandateRow selectByIdForUpdate(@Param("id") long id);
    int adminScheduleRevocationRetry(@Param("id") long id,
                                     @Param("expectedVersion") long expectedVersion,
                                     @Param("now") Date now);
    List<ReadingSubscriptionMandateAdminAuditRow> selectMandateAdminAudits(
        @Param("mandateId") long mandateId, @Param("limit") int limit);
    int insertMandateAdminAudit(@Param("mandateId") long mandateId,
                                @Param("userId") long userId,
                                @Param("operatorId") long operatorId,
                                @Param("action") String action,
                                @Param("beforeStatus") String beforeStatus,
                                @Param("afterStatus") String afterStatus,
                                @Param("reason") String reason,
                                @Param("now") Date now);
    long countPendingRevocations();
}
