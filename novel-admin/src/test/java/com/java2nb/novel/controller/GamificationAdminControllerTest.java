package com.java2nb.novel.controller;

import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GamificationAdminControllerTest {

    @Test
    void separatesViewAndGrantPermissions() throws Exception {
        assertPermission("index", "novel:gamification:view");
        assertPermission("listAccounts", "novel:gamification:view", Map.class);
        assertPermission("listLedger", "novel:gamification:view", Map.class);
        assertPermission("listJobs", "novel:gamification:view", Map.class);
        assertPermission("listSeasons", "novel:gamification:view", Map.class);
        assertPermission("reconcileSeason", "novel:gamification:view", long.class);
        assertPermission("grant", "novel:gamification:grant", long.class, long.class,
            String.class, long.class, String.class);
        assertPermission("closeSeason", "novel:gamification:finalize", long.class);
        assertPermission("pauseSeason", "novel:gamification:finalize", long.class, String.class);
        assertPermission("retrySeason", "novel:gamification:finalize", long.class);
        assertPermission("finalizeSeason", "novel:gamification:finalize", long.class);
        assertPermission("createSpecialSeason", "novel:gamification:finalize", String.class,
            String.class, long.class, long.class, long.class);
        assertPermission("listTickerNicknames", "novel:gamification:view", Map.class);
        assertPermission("moderateTicker", "novel:gamification:review", long.class, boolean.class,
            String.class);
        assertPermission("listRewardCampaigns", "novel:gamification:view", Map.class);
        assertPermission("listRewardAllocations", "novel:gamification:view", Map.class);
        assertPermission("listQuestCampaigns", "novel:gamification:view", Map.class);
        assertPermission("listQuestRewards", "novel:gamification:view", Map.class);
        assertPermission("listPublicPolicies", "novel:gamification:config", Map.class);
        assertPermission("createPublicPolicy", "novel:gamification:config",
            String.class, String.class, String.class);
        assertPermission("publishPublicPolicy", "novel:gamification:config",
            long.class, long.class);
        assertPermission("createQuestCampaign", "novel:gamification:config",
            String.class, long.class, long.class);
        assertPermission("saveQuestReward", "novel:gamification:config",
            long.class, String.class, long.class, long.class);
        assertPermission("activateQuestCampaign", "novel:gamification:config", long.class);
        assertPermission("closeQuestCampaign", "novel:gamification:config", long.class);
        assertPermission("calculateReward", "novel:gamification:reward", long.class, long.class, String.class);
        assertPermission("approveReward", "novel:gamification:reward", long.class);
        assertPermission("postReward", "novel:gamification:reward", long.class);
        assertPermission("clawbackReward", "novel:gamification:adjust", long.class, String.class);
    }

    private void assertPermission(String methodName, String permission,
                                  Class<?>... parameterTypes) throws Exception {
        Method method = GamificationAdminController.class.getDeclaredMethod(methodName, parameterTypes);
        RequiresPermissions annotation = method.getAnnotation(RequiresPermissions.class);
        assertThat(annotation).isNotNull();
        assertThat(annotation.value()).containsExactly(permission);
    }
}
