package com.java2nb.novel.service.gift;

import lombok.Data;

import java.util.Date;

@Data
public class GiftCampaignRow {
    private Long id;
    private String campaignCode;
    private String campaignName;
    private String rewardType;
    private Long rewardAmount;
    private Integer ticketValidityDays;
    private Date startAt;
    private Date endAt;
    private Long maxRedemptions;
    private Long redeemedCount;
    private Integer maxPerUser;
    private String status;
    private String policyVersion;
    private Long version;
}
