package com.java2nb.novel.mapper;

import com.java2nb.novel.service.subscription.ReadingSubscriptionPlanRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPurchaseRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface ReadingSubscriptionPurchaseMapper {
    ReadingSubscriptionPurchaseRow selectByUserRequest(@Param("userId") long userId,
                                                        @Param("clientRequestId") String clientRequestId);
    ReadingSubscriptionPurchaseRow selectOpenByUser(@Param("userId") long userId);
    ReadingSubscriptionPurchaseRow selectByOutTradeNoForUpdate(@Param("outTradeNo") long outTradeNo);
    ReadingSubscriptionPurchaseRow selectByIdForUpdate(@Param("purchaseId") long purchaseId);
    ReadingSubscriptionPurchaseRow selectById(@Param("purchaseId") long purchaseId);
    List<ReadingSubscriptionPurchaseRow> selectReviews(@Param("status") String status,
                                                       @Param("offset") long offset,
                                                       @Param("limit") int limit);
    long countReviews(@Param("status") String status);
    int insertPurchase(@Param("outTradeNo") long outTradeNo, @Param("userId") long userId,
                       @Param("plan") ReadingSubscriptionPlanRow plan,
                       @Param("payChannel") byte payChannel,
                       @Param("clientRequestId") String clientRequestId,
                       @Param("requestHash") String requestHash,
                       @Param("policyVersion") String policyVersion,
                       @Param("zoneId") String zoneId,
                       @Param("createdAt") Date createdAt);
    int markActivated(@Param("purchaseId") long purchaseId,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("subscriptionId") long subscriptionId,
                      @Param("settledAt") Date settledAt);
    int markPaidReview(@Param("purchaseId") long purchaseId,
                       @Param("expectedVersion") long expectedVersion,
                       @Param("settledAt") Date settledAt);
    int markFailed(@Param("purchaseId") long purchaseId,
                   @Param("expectedVersion") long expectedVersion,
                   @Param("settledAt") Date settledAt);
    int markReviewActivated(@Param("purchaseId") long purchaseId,
                            @Param("expectedVersion") long expectedVersion,
                            @Param("subscriptionId") long subscriptionId,
                            @Param("updatedAt") Date updatedAt);
    int markRefundPending(@Param("purchaseId") long purchaseId,
                          @Param("expectedVersion") long expectedVersion,
                          @Param("updatedAt") Date updatedAt);
    int insertReviewAudit(@Param("purchase") ReadingSubscriptionPurchaseRow purchase,
                          @Param("eventType") String eventType,
                          @Param("toStatus") String toStatus,
                          @Param("operatorId") long operatorId,
                          @Param("reason") String reason,
                          @Param("versionAfter") long versionAfter,
                          @Param("createdAt") Date createdAt);
}
