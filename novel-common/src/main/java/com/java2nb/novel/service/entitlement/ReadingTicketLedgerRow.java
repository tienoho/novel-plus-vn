package com.java2nb.novel.service.entitlement;

import lombok.Data;

@Data
public class ReadingTicketLedgerRow {
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
    private String policyVersion;
}
