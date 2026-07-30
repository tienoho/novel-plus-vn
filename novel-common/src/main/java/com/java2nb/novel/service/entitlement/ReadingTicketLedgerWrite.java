package com.java2nb.novel.service.entitlement;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class ReadingTicketLedgerWrite {
    String entryNo;
    long userId;
    String entryType;
    long amount;
    long balanceAfter;
    String businessType;
    String businessId;
    String idempotencyKey;
    String requestHash;
    String operatorType;
    Long operatorId;
    String reason;
    String policyVersion;
}
