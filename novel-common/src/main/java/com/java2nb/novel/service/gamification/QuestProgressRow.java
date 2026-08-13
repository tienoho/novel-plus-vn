package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

@Data
public class QuestProgressRow {
    private String questCode;
    private String periodType;
    private String periodKey;
    private Integer targetCount;
    private String nameKey;
    private Integer sortNo;
    private Integer currentCount;
    private Date completedAt;
    private Long claimId;
    private Date claimedAt;
    private Long expReward;
    private Long ticketReward;
}
