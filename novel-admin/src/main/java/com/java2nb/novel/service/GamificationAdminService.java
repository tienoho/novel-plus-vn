package com.java2nb.novel.service;

import com.java2nb.novel.service.gamification.TicketPostResult;
import com.java2nb.novel.service.gamification.SeasonPhaseResult;
import com.java2nb.novel.service.gamification.MonthlyRankDriftRow;
import com.java2nb.novel.service.gamification.RewardCampaignRow;
import com.java2nb.novel.service.gamification.AuthorRewardAllocationRow;

import java.util.List;
import java.util.Map;

public interface GamificationAdminService {

    List<Map<String, Object>> listAccounts(Map<String, Object> params);
    int countAccounts(Map<String, Object> params);

    List<Map<String, Object>> listLedger(Map<String, Object> params);
    int countLedger(Map<String, Object> params);

    List<Map<String, Object>> listJobs(Map<String, Object> params);
    int countJobs(Map<String, Object> params);

    List<Map<String, Object>> listSeasons(Map<String, Object> params);
    int countSeasons(Map<String, Object> params);

    List<Map<String, Object>> listRewardCampaigns(Map<String, Object> params);
    int countRewardCampaigns(Map<String, Object> params);
    List<Map<String, Object>> listRewardAllocations(Map<String, Object> params);
    int countRewardAllocations(Map<String, Object> params);

    List<Map<String, Object>> listQuestCampaigns(Map<String, Object> params);
    int countQuestCampaigns(Map<String, Object> params);
    List<Map<String, Object>> listQuestRewards(Map<String, Object> params);
    int countQuestRewards(Map<String, Object> params);

    TicketPostResult grant(long userId, long amount, String clientRequestId, long effectiveAtMillis,
                           String reason, long actorId, boolean canAdjust);

    SeasonPhaseResult closeSeason(long seasonId, long actorId);
    SeasonPhaseResult pauseSeason(long seasonId, String reason, long actorId);
    SeasonPhaseResult retrySeason(long seasonId, long actorId);
    List<MonthlyRankDriftRow> reconcileSeason(long seasonId);
    SeasonPhaseResult finalizeSeason(long seasonId, long actorId);

    RewardCampaignRow calculateRewardCampaign(long seasonId, long budgetXu, String sharesBps);
    RewardCampaignRow approveRewardCampaign(long campaignId, long actorId);
    int postRewardCampaign(long campaignId);
    AuthorRewardAllocationRow clawbackReward(long allocationId, String reason, long actorId);

    com.java2nb.novel.service.gamification.QuestCampaignRow createQuestCampaign(
        String campaignCode, long startAtMillis, long endAtMillis, long actorId);
    void saveQuestReward(long campaignId, String questCode, long expAmount,
                         long ticketAmount, long actorId);
    com.java2nb.novel.service.gamification.QuestCampaignRow activateQuestCampaign(
        long campaignId, long actorId);
    com.java2nb.novel.service.gamification.QuestCampaignRow closeQuestCampaign(
        long campaignId, long actorId);
}
