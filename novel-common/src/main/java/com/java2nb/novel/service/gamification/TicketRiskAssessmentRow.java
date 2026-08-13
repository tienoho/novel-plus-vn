package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

@Data
public class TicketRiskAssessmentRow {
    private Long id;
    private Long userId;
    private Long seasonId;
    private Long bookId;
    private String clientRequestId;
    private String deviceHash;
    private String ipHash;
    private Integer riskScore;
    private String action;
    private String matchedRules;
    private String policyVersion;
    private Date createTime;
}
