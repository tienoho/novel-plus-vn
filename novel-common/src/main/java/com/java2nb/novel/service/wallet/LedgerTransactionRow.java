package com.java2nb.novel.service.wallet;

import lombok.Data;

@Data
public class LedgerTransactionRow {

    private Long id;
    private String idempotencyKey;
    private String requestHash;
    private String businessType;
    private String businessId;
    private Long totalAmount;
    private Long reversalOfTransactionId;
}
