package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

@Data
public class UserExpLedgerRow {
    private Long id;
    private Long userId;
    private String sourceKey;
    private String sourceType;
    private Long amount;
    private Long balanceAfter;
    private String ruleVersion;
    private String policyVersion;
    private Date createTime;
}
