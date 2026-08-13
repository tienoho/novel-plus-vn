package com.java2nb.novel.service.gamification.config;

import lombok.Data;

@Data
public class GamificationQuestRewardRow {
    private String policyVersion;
    private String questCode;
    private String campaignCode;
    private String rewardType;
    private Long amount;
}
