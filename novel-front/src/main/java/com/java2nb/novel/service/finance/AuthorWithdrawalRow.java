package com.java2nb.novel.service.finance;

import lombok.Data;

import java.util.Date;

@Data
public class AuthorWithdrawalRow {

    private Long id;
    private String withdrawalNo;
    private String idempotencyKey;
    private String holdIdempotencyKey;
    private Long authorId;
    private Long userId;
    private Long requestedXu;
    private Long grossAmountVnd;
    private Long withheldTaxVnd;
    private Long netAmountVnd;
    private String bankCode;
    private String bankAccountLast4;
    private String status;
    private String releaseTargetStatus;
    private String payoutProvider;
    private Date requestedAt;
    private String rejectionReason;
    private Long version;
}
