package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

@Data
public class QuestCampaignRow {
    private Long id;
    private String campaignCode;
    private Date startAt;
    private Date endAt;
    private String status;
    private String policyVersion;
    private Date createTime;
    private Date updateTime;
}
