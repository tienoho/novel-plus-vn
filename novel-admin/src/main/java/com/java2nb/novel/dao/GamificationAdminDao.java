package com.java2nb.novel.dao;

import com.java2nb.common.annotation.SanitizeMap;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;
import java.util.Map;

/** Truy vấn báo cáo gamification; mọi thao tác ghi phải đi qua service trong novel-common. */
@Mapper
public interface GamificationAdminDao {

    List<Map<String, Object>> listAccounts(@SanitizeMap Map<String, Object> params);
    int countAccounts(Map<String, Object> params);

    List<Map<String, Object>> listLedger(@SanitizeMap Map<String, Object> params);
    int countLedger(Map<String, Object> params);

    List<Map<String, Object>> listJobs(@SanitizeMap Map<String, Object> params);
    int countJobs(Map<String, Object> params);

    List<Map<String, Object>> listSeasons(@SanitizeMap Map<String, Object> params);
    int countSeasons(Map<String, Object> params);

    List<Map<String, Object>> listRewardCampaigns(@SanitizeMap Map<String, Object> params);
    int countRewardCampaigns(Map<String, Object> params);

    List<Map<String, Object>> listRewardAllocations(@SanitizeMap Map<String, Object> params);
    int countRewardAllocations(Map<String, Object> params);

    List<Map<String, Object>> listQuestCampaigns(@SanitizeMap Map<String, Object> params);
    int countQuestCampaigns(Map<String, Object> params);

    List<Map<String, Object>> listQuestRewards(@SanitizeMap Map<String, Object> params);
    int countQuestRewards(Map<String, Object> params);

    List<Map<String, Object>> listTickerNicknames(@SanitizeMap Map<String, Object> params);
    int countTickerNicknames(Map<String, Object> params);

    List<Map<String, Object>> listRiskReviews(@SanitizeMap Map<String, Object> params);
    int countRiskReviews(Map<String, Object> params);

    List<Map<String, Object>> listPublicPolicies(@SanitizeMap Map<String, Object> params);
    int countPublicPolicies(Map<String, Object> params);
}
