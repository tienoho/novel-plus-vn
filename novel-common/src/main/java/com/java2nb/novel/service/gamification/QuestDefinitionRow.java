package com.java2nb.novel.service.gamification;

import lombok.Data;

@Data
public class QuestDefinitionRow {
    private String policyVersion;
    private String questCode;
    private String eventType;
    private String periodType;
    private Integer targetCount;
    private String nameKey;
    private Integer sortNo;
    private Boolean active;
}
