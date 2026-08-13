package com.java2nb.novel.service.gamification;

import lombok.Data;

@Data
public class LevelRewardPolicyRow {
    private String policyVersion;
    private Integer level;
    private Long ticketAmount;
    private Integer ticketValidityDays;
}
