package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

@Data
public class RewardCampaignRow {
    private Long id;
    private String periodCode;
    private String status;
    private Boolean enabled;
    private Long budgetXu;
    private String structureJson;
    private String policyVersion;
    private Long approvedBy;
    private Date approvedAt;
    private Long version;
}
