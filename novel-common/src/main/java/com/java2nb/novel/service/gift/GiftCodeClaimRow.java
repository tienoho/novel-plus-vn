package com.java2nb.novel.service.gift;

import lombok.Data;

import java.util.Date;

@Data
public class GiftCodeClaimRow {
    private Long codeId;
    private Long campaignId;
    private String codeStatus;
    private String campaignStatus;
    private String rewardType;
    private Long rewardAmount;
    private Integer ticketValidityDays;
    private Date startAt;
    private Date endAt;
    private Long codeMaxRedemptions;
    private Long codeRedeemedCount;
    private Long campaignMaxRedemptions;
    private Long campaignRedeemedCount;
    private Integer maxPerUser;
}
