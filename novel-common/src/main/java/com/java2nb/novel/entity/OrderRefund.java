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
public class OrderRefund implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String refundNo;
    private Long outTradeNo;
    private Long userId;
    private Integer refundAmountVnd;
    private Long refundXu;
    private String type; // REFUND, CHARGEBACK
    private String status; // REQUESTED, APPROVED, REVERSED, REJECTED, FAILED
    private String reason;
    private Long originalLedgerTransactionId;
    private Long holdLedgerTransactionId;
    private Long reversalLedgerTransactionId;
    private String providerReference;
    private String idempotencyKey;
    private Long processedBy;
    private Date processedAt;
    private Date createTime;
    private Date updateTime;
    private Long version;
}
