package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
public class GamificationProfileRow {
    private Long id;
    private Long userId;
    private Integer level;
    private Long totalExp;
    private String ruleVersion;
    private String realmCode;
    private String frameCode;
    private Integer checkinStreak;
    private Integer longestStreak;
    private LocalDate lastCheckinDate;
    private Date realmChangedAt;
    private Boolean tickerOptOut;
    private Long version;
}
