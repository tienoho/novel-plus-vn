package com.java2nb.novel.service.gamification;

import lombok.Data;

@Data
public class TicketRiskRuleRow {
    private String ruleCode;
    private String metricName;
    private Long thresholdValue;
    private Integer windowMinutes;
    private Integer score;
    private Boolean hardBlock;
}
