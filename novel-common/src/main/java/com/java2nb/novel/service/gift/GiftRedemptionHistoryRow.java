package com.java2nb.novel.service.gift;

import lombok.Data;

import java.util.Date;

@Data
public class GiftRedemptionHistoryRow {
    private Long id;
    private Long campaignId;
    private String campaignCode;
    private String campaignName;
    private Long codeId;
    private String codeHint;
    private Long userId;
    private String rewardType;
    private Long rewardAmount;
    private Date redeemedAt;
}
