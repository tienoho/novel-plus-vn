package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

@Data
public class TicketLedgerRow {

    private Long id;
    private String entryNo;
    private Long userId;
    private String entryType;
    private Long amount;
    private Long balanceAfter;
    private String businessType;
    private String businessId;
    private String idempotencyKey;
    private String requestHash;
    private Long seasonId;
    private Long bookId;
    private String operatorType;
    private Long operatorId;
    private String reason;
    private String policyVersion;
    private Date createTime;
}
