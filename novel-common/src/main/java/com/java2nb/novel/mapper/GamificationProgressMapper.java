package com.java2nb.novel.mapper;

import com.java2nb.novel.service.gamification.GamificationEventInput;
import com.java2nb.novel.service.gamification.GamificationEventRow;
import com.java2nb.novel.service.gamification.GamificationProfileRow;
import com.java2nb.novel.service.gamification.RealmCatalogRow;
import com.java2nb.novel.service.gamification.LevelRuleRow;
import com.java2nb.novel.service.gamification.QuestDefinitionRow;
import com.java2nb.novel.service.gamification.QuestCampaignRow;
import com.java2nb.novel.service.gamification.QuestProgressRow;
import com.java2nb.novel.service.gamification.QuestRewardSummary;
import com.java2nb.novel.service.gamification.QuestClaimRow;
import com.java2nb.novel.service.gamification.UserExpLedgerRow;
import com.java2nb.novel.service.gamification.LevelRewardGrantRow;
import com.java2nb.novel.service.gamification.LevelRewardPolicyRow;

import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GamificationProgressMapper {

    /**
     * Ghi sự kiện nguồn theo cơ chế hai pha. Trả về 0 khi {@code source_key} đã tồn tại; bên gọi
     * phải đọc lại và so sánh payload hash, không được coi mọi trường hợp 0 dòng là thành công.
     */
    int insertEventIgnore(@Param("event") GamificationEventInput event);

    GamificationEventRow selectEventBySourceKey(@Param("sourceKey") String sourceKey);

    List<Long> selectPendingEventIds(@Param("limit") int limit,
                                     @Param("maxAttempt") int maxAttempt);

    long countPendingEvents(@Param("maxAttempt") int maxAttempt);

    GamificationEventRow selectEventById(@Param("eventId") long eventId);

    int claimEvent(@Param("eventId") long eventId,
                   @Param("expectedVersion") long expectedVersion,
                   @Param("claimedAt") java.util.Date claimedAt,
                   @Param("maxAttempt") int maxAttempt);

    int completeEvent(@Param("eventId") long eventId,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("status") String status,
                      @Param("processedAt") java.util.Date processedAt);

    int recordEventFailure(@Param("eventId") long eventId,
                           @Param("expectedVersion") long expectedVersion,
                           @Param("failedAt") java.util.Date failedAt,
                           @Param("errorMessage") String errorMessage,
                           @Param("maxAttempt") int maxAttempt);

    List<QuestDefinitionRow> selectActiveQuestsByEventType(@Param("eventType") String eventType);

    int insertQuestProgressIgnore(@Param("userId") long userId,
                                  @Param("questCode") String questCode,
                                  @Param("periodKey") String periodKey,
                                  @Param("targetCount") int targetCount);

    int incrementQuestProgress(@Param("userId") long userId,
                               @Param("questCode") String questCode,
                               @Param("periodKey") String periodKey,
                               @Param("completedAt") java.util.Date completedAt);

    List<QuestCampaignRow> selectActiveQuestCampaigns(@Param("observedAt") java.util.Date observedAt);

    List<QuestProgressRow> selectQuestProgress(@Param("userId") long userId,
                                               @Param("dailyKey") String dailyKey,
                                               @Param("weeklyKey") String weeklyKey,
                                               @Param("campaignCode") String campaignCode);

    QuestDefinitionRow selectQuestByCode(@Param("questCode") String questCode);

    QuestProgressRow lockQuestProgress(@Param("userId") long userId,
                                       @Param("questCode") String questCode,
                                       @Param("periodKey") String periodKey);

    QuestClaimRow selectQuestClaim(@Param("userId") long userId,
                                   @Param("questCode") String questCode,
                                   @Param("periodKey") String periodKey);

    QuestRewardSummary selectQuestRewardSummary(@Param("questCode") String questCode,
                                                 @Param("campaignCode") String campaignCode);

    int insertExpLedger(@Param("userId") long userId,
                        @Param("sourceKey") String sourceKey,
                        @Param("sourceType") String sourceType,
                        @Param("amount") long amount,
                        @Param("balanceAfter") long balanceAfter,
                        @Param("ruleVersion") String ruleVersion,
                        @Param("policyVersion") String policyVersion);

    UserExpLedgerRow selectExpLedgerBySourceKey(@Param("sourceKey") String sourceKey);

    LevelRuleRow selectLevelRuleForExp(@Param("ruleVersion") String ruleVersion,
                                       @Param("totalExp") long totalExp);

    List<LevelRuleRow> selectLevelRulesBetween(@Param("ruleVersion") String ruleVersion,
                                               @Param("fromLevel") int fromLevel,
                                               @Param("toLevel") int toLevel);

    LevelRewardPolicyRow selectLevelRewardPolicy(@Param("policyVersion") String policyVersion,
                                                 @Param("level") int level);

    LevelRewardGrantRow selectLevelRewardGrant(@Param("userId") long userId,
                                               @Param("level") int level,
                                               @Param("policyVersion") String policyVersion);

    int insertLevelRewardGrantIgnore(@Param("eventId") long eventId,
                                     @Param("userId") long userId,
                                     @Param("level") int level,
                                     @Param("policyVersion") String policyVersion,
                                     @Param("ticketAmount") long ticketAmount,
                                     @Param("ticketLedgerId") long ticketLedgerId,
                                     @Param("idempotencyKey") String idempotencyKey);

    int updateProfileExp(@Param("userId") long userId,
                         @Param("expectedVersion") long expectedVersion,
                         @Param("totalExp") long totalExp,
                         @Param("level") int level,
                         @Param("frameCode") String frameCode);

    int updateCheckIn(@Param("userId") long userId,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("lastCheckInDate") java.time.LocalDate lastCheckInDate,
                      @Param("checkInStreak") int checkInStreak,
                      @Param("longestStreak") int longestStreak);

    int insertQuestClaim(@Param("userId") long userId,
                         @Param("questCode") String questCode,
                         @Param("periodKey") String periodKey,
                         @Param("campaignCode") String campaignCode,
                         @Param("expAmount") long expAmount,
                         @Param("ticketAmount") long ticketAmount,
                         @Param("expLedgerId") Long expLedgerId,
                         @Param("ticketLedgerId") Long ticketLedgerId,
                         @Param("idempotencyKey") String idempotencyKey,
                         @Param("policyVersion") String policyVersion);

    int insertProfileIgnore(@Param("userId") long userId,
                            @Param("ruleVersion") String ruleVersion);

    GamificationProfileRow selectProfile(@Param("userId") long userId);

    GamificationProfileRow lockProfile(@Param("userId") long userId);

    RealmCatalogRow selectRealm(@Param("realmCode") String realmCode);

    LevelRuleRow selectNextLevelRule(@Param("ruleVersion") String ruleVersion,
                                     @Param("totalExp") long totalExp);

    int updateRealm(@Param("userId") long userId,
                    @Param("realmCode") String realmCode,
                    @Param("changedAt") java.util.Date changedAt,
                    @Param("expectedVersion") long expectedVersion);

    int insertProfileAudit(@Param("userId") long userId,
                           @Param("changeType") String changeType,
                           @Param("fromValue") String fromValue,
                           @Param("toValue") String toValue,
                           @Param("operatorType") String operatorType,
                           @Param("operatorId") Long operatorId,
                           @Param("reason") String reason);

    int updateTickerOptOut(@Param("userId") long userId,
                           @Param("optOut") boolean optOut,
                           @Param("expectedVersion") long expectedVersion);
}
