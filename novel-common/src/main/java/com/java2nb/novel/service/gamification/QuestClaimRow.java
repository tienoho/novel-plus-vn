package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

@Data
public class QuestClaimRow {
    private Long id;
    private Long userId;
    private String questCode;
    private String periodKey;
    private String campaignCode;
    private Long expAmount;
    private Long ticketAmount;
    private Long expLedgerId;
    private Long ticketLedgerId;
    private String idempotencyKey;
    private String policyVersion;
    private Date createTime;
}
