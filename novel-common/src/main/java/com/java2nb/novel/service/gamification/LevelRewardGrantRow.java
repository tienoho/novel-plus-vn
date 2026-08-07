package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

@Data
public class LevelRewardGrantRow {
    private Long id;
    private Long eventId;
    private Long userId;
    private Integer level;
    private String policyVersion;
    private Long ticketAmount;
    private Long ticketLedgerId;
    private String idempotencyKey;
    private Date createTime;
}
