package com.java2nb.novel.service.gift;

import lombok.Data;

import java.util.Date;

@Data
public class GiftCodeRow {
    private Long id;
    private Long campaignId;
    private String codeHint;
    private Long maxRedemptions;
    private Long redeemedCount;
    private String status;
    private Long version;
    private Date createTime;
}
