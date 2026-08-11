package com.java2nb.novel.service.gamification.config;

import lombok.Data;

import java.util.Date;

@Data
public class GamificationPolicyBundleRow {
    private Long id;
    private String policyVersion;
    private String status;
    private String contentHash;
    private Long createdBy;
    private Long submittedBy;
    private Long approvedBy;
    private Long publishedBy;
    private String changeReason;
    private Date submittedAt;
    private Date approvedAt;
    private Date publishedAt;
    private Date archivedAt;
    private Long version;
    private Date createTime;
    private Date updateTime;
}
