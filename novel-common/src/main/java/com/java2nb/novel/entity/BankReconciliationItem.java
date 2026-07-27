package com.java2nb.novel.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankReconciliationItem implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long batchId;
    private Long outTradeNo;
    private String bankTradeNo;
    private Integer amountVnd;
    private String matchStatus; // MATCHED, AMOUNT_MISMATCH, NOT_FOUND_IN_SYSTEM, NOT_FOUND_IN_BANK
    private String discrepancyReason;
    private Date createTime;
}
