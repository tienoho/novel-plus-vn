package com.java2nb.novel.service.gamification;

import lombok.Data;

@Data
public class LevelRuleRow {
    private String ruleVersion;
    private Integer level;
    private Long minExp;
    private String titleKey;
    private String frameCode;
}
