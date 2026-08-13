package com.java2nb.novel.mapper;

import com.java2nb.novel.service.gamification.QuestCampaignDraftCommand;
import com.java2nb.novel.service.gamification.QuestCampaignRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;

@Mapper
public interface QuestCampaignConfigMapper {
    int insertDraft(@Param("command") QuestCampaignDraftCommand command);

    QuestCampaignRow selectByCode(@Param("campaignCode") String campaignCode);

    QuestCampaignRow selectById(@Param("campaignId") long campaignId);

    QuestCampaignRow lockById(@Param("campaignId") long campaignId);

    int countQuest(@Param("questCode") String questCode);

    int deleteRewardTypes(@Param("campaignCode") String campaignCode,
                          @Param("questCode") String questCode);

    int insertReward(@Param("campaignCode") String campaignCode,
                     @Param("questCode") String questCode,
                     @Param("rewardType") String rewardType,
                     @Param("amount") long amount);

    int countRewards(@Param("campaignCode") String campaignCode);

    int countOverlappingActive(@Param("campaignId") long campaignId,
                               @Param("startAt") Date startAt,
                               @Param("endAt") Date endAt);

    int updateStatus(@Param("campaignId") long campaignId,
                     @Param("expectedStatus") String expectedStatus,
                     @Param("status") String status);
}
