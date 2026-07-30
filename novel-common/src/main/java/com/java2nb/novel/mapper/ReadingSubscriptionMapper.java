package com.java2nb.novel.mapper;

import com.java2nb.novel.service.subscription.ReadingSubscriptionActivationCommand;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPlanRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPeriodGrantRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPlanCommand;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPurchaseActivationCommand;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface ReadingSubscriptionMapper {
    ReadingSubscriptionPlanRow selectActivePlanByCode(@Param("planCode") String planCode);
    ReadingSubscriptionRow selectSubscriptionBySource(@Param("sourceType") String sourceType,
                                                      @Param("sourceRef") String sourceRef);
    ReadingSubscriptionRow selectSubscriptionBySourceForUpdate(
        @Param("sourceType") String sourceType, @Param("sourceRef") String sourceRef);
    List<ReadingSubscriptionPlanRow> selectActivePlans();
    List<ReadingSubscriptionPlanRow> selectAllPlans();
    ReadingSubscriptionPlanRow selectPlanById(@Param("planId") long planId);
    ReadingSubscriptionPlanRow selectPlanByCode(@Param("planCode") String planCode);
    int insertPlan(@Param("command") ReadingSubscriptionPlanCommand command);
    int updatePlan(@Param("planId") long planId, @Param("expectedVersion") long expectedVersion,
                   @Param("command") ReadingSubscriptionPlanCommand command);
    int updatePlanStatus(@Param("planId") long planId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("status") String status);
    ReadingSubscriptionRow selectCurrentSubscriptionByUserId(@Param("userId") long userId);
    List<ReadingSubscriptionPeriodGrantRow> selectPeriodGrantsByUser(
        @Param("userId") long userId, @Param("subscriptionId") long subscriptionId,
        @Param("limit") int limit);
    int insertSubscription(@Param("plan") ReadingSubscriptionPlanRow plan,
                           @Param("command") ReadingSubscriptionActivationCommand command);
    int insertPurchasedSubscription(
        @Param("command") ReadingSubscriptionPurchaseActivationCommand command);
    ReadingSubscriptionRow lockDueSubscription(@Param("subscriptionId") long subscriptionId,
                                               @Param("now") Date now);
    int insertPeriodGrantFromLedger(@Param("subscriptionId") long subscriptionId,
                                    @Param("userId") long userId,
                                    @Param("periodStart") Date periodStart,
                                    @Param("periodEnd") Date periodEnd,
                                    @Param("ticketAmount") long ticketAmount,
                                    @Param("ticketExpireAt") Date ticketExpireAt,
                                    @Param("idempotencyKey") String idempotencyKey);
    int advanceSubscription(@Param("subscriptionId") long subscriptionId,
                            @Param("expectedVersion") long expectedVersion,
                            @Param("nextGrantAt") Date nextGrantAt,
                            @Param("nextStatus") String nextStatus);
    List<Long> selectDueSubscriptionIds(@Param("now") Date now, @Param("limit") int limit);
}
