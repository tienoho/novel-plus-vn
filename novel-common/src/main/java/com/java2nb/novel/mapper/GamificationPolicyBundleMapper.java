package com.java2nb.novel.mapper;

import com.java2nb.novel.service.gamification.GamificationPublicPolicyRow;
import com.java2nb.novel.service.gamification.LevelRewardPolicyRow;
import com.java2nb.novel.service.gamification.LevelRuleRow;
import com.java2nb.novel.service.gamification.QuestDefinitionRow;
import com.java2nb.novel.service.gamification.RealmCatalogRow;
import com.java2nb.novel.service.gamification.TicketRiskPolicyRow;
import com.java2nb.novel.service.gamification.TicketRiskRuleRow;
import com.java2nb.novel.service.gamification.config.GamificationPolicyBundleRow;
import com.java2nb.novel.service.gamification.config.GamificationQuestRewardRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface GamificationPolicyBundleMapper {
    List<GamificationPolicyBundleRow> selectBundles(@Param("limit") int limit);
    GamificationPolicyBundleRow selectBundle(@Param("policyVersion") String policyVersion);
    GamificationPolicyBundleRow lockBundle(@Param("policyVersion") String policyVersion);
    int insertDraft(@Param("bundle") GamificationPolicyBundleRow bundle);

    int cloneLevelRules(@Param("source") String source, @Param("target") String target);
    int cloneLevelRewards(@Param("source") String source, @Param("target") String target);
    int cloneQuests(@Param("source") String source, @Param("target") String target);
    int cloneQuestRewards(@Param("source") String source, @Param("target") String target);
    int cloneRealms(@Param("source") String source, @Param("target") String target);
    int cloneAbusePolicy(@Param("source") String source, @Param("target") String target);
    int cloneAbuseRules(@Param("source") String source, @Param("target") String target);
    int clonePublicPolicy(@Param("source") String source, @Param("target") String target);

    List<LevelRuleRow> selectLevels(@Param("policyVersion") String policyVersion);
    List<LevelRewardPolicyRow> selectLevelRewards(@Param("policyVersion") String policyVersion);
    List<QuestDefinitionRow> selectQuests(@Param("policyVersion") String policyVersion);
    List<GamificationQuestRewardRow> selectQuestRewards(@Param("policyVersion") String policyVersion);
    List<RealmCatalogRow> selectRealms(@Param("policyVersion") String policyVersion);
    TicketRiskPolicyRow selectAbusePolicy(@Param("policyVersion") String policyVersion);
    List<TicketRiskRuleRow> selectAbuseRules(@Param("policyVersion") String policyVersion);
    GamificationPublicPolicyRow selectPublicPolicy(@Param("policyVersion") String policyVersion);
    List<String> selectCanonicalLines(@Param("policyVersion") String policyVersion);

    int upsertLevel(@Param("policyVersion") String policyVersion, @Param("row") LevelRuleRow row);
    int upsertLevelReward(@Param("policyVersion") String policyVersion,
                          @Param("row") LevelRewardPolicyRow row);
    int upsertQuest(@Param("policyVersion") String policyVersion,
                    @Param("row") QuestDefinitionRow row);
    int deleteDefaultQuestRewards(@Param("policyVersion") String policyVersion,
                                  @Param("questCode") String questCode);
    int insertQuestReward(@Param("policyVersion") String policyVersion,
                          @Param("questCode") String questCode,
                          @Param("rewardType") String rewardType,
                          @Param("amount") long amount);
    int upsertRealm(@Param("policyVersion") String policyVersion,
                    @Param("row") RealmCatalogRow row);
    int upsertAbusePolicy(@Param("policyVersion") String policyVersion,
                          @Param("reviewScoreThreshold") int reviewScoreThreshold);
    int upsertAbuseRule(@Param("policyVersion") String policyVersion,
                        @Param("row") TicketRiskRuleRow row);
    int upsertPublicPolicy(@Param("policyVersion") String policyVersion,
                           @Param("title") String title,
                           @Param("contentText") String contentText);

    int updateDraftHash(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
                        @Param("contentHash") String contentHash, @Param("reason") String reason);
    int submit(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
               @Param("operatorId") long operatorId, @Param("at") Date at,
               @Param("reason") String reason, @Param("contentHash") String contentHash);
    int approve(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
                @Param("operatorId") long operatorId, @Param("at") Date at,
                @Param("reason") String reason);
    int publish(@Param("id") long id, @Param("expectedVersion") long expectedVersion,
                @Param("operatorId") long operatorId, @Param("at") Date at,
                @Param("reason") String reason);
    int publishPublicPolicy(@Param("policyVersion") String policyVersion,
                            @Param("operatorId") long operatorId, @Param("at") Date at);
    int insertPublicPolicyAudit(@Param("policyVersion") String policyVersion,
                                @Param("eventType") String eventType,
                                @Param("fromStatus") String fromStatus,
                                @Param("toStatus") String toStatus,
                                @Param("operatorId") long operatorId);
    int insertAudit(@Param("policyId") long policyId, @Param("eventType") String eventType,
                    @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus,
                    @Param("operatorId") long operatorId, @Param("reason") String reason,
                    @Param("beforeHash") String beforeHash, @Param("afterHash") String afterHash);
}
