package com.java2nb.novel.service.gift;

import lombok.Data;

import java.util.Date;

@Data
public class GiftRedemptionRow {
    private Long id;
    private Long campaignId;
    private Long codeId;
    private Long userId;
    private String rewardType;
    private Long rewardAmount;
    private String clientRequestId;
    private Date redeemedAt;
}
