package com.java2nb.novel.common.entity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

/**
 * Domain entity for table financial_voucher.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FinancialVoucherDO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String voucherNo;       // e.g. INV-20260725-00001 or VOUCHER-20260725-00001
    private String voucherType;     // RECHARGE_RECEIPT or AUTHOR_PAYOUT_VOUCHER
    private String referenceType;   // ORDER_PAY or AUTHOR_WITHDRAWAL_REQUEST
    private String referenceId;     // order_no or withdrawal_id
    private String payerName;
    private String payerTaxCode;
    private String payeeName;
    private String payeeTaxCode;
    private Long grossAmountVnd;
    private Long taxAmountVnd;     // VAT for recharge, PIT for payout
    private Long netAmountVnd;
    private String currency;        // VND
    private String status;          // ISSUED, CANCELLED
    private Date issuedAt;
    private Date createTime;
    private Date updateTime;
}
