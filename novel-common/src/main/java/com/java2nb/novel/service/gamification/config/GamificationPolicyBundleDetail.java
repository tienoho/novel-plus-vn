package com.java2nb.novel.service.gamification.config;

import com.java2nb.novel.service.gamification.GamificationPublicPolicyRow;
import com.java2nb.novel.service.gamification.LevelRewardPolicyRow;
import com.java2nb.novel.service.gamification.LevelRuleRow;
import com.java2nb.novel.service.gamification.QuestDefinitionRow;
import com.java2nb.novel.service.gamification.RealmCatalogRow;
import com.java2nb.novel.service.gamification.TicketRiskPolicyRow;
import com.java2nb.novel.service.gamification.TicketRiskRuleRow;
import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class GamificationPolicyBundleDetail {
    GamificationPolicyBundleRow bundle;
    List<LevelRuleRow> levels;
    List<LevelRewardPolicyRow> levelRewards;
    List<QuestDefinitionRow> quests;
    List<GamificationQuestRewardRow> questRewards;
    List<RealmCatalogRow> realms;
    TicketRiskPolicyRow abusePolicy;
    List<TicketRiskRuleRow> abuseRules;
    GamificationPublicPolicyRow publicPolicy;
}
