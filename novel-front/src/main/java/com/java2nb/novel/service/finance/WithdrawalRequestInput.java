package com.java2nb.novel.service.finance;

import lombok.Data;

@Data
public class WithdrawalRequestInput {

    private Long amountXu;
    private String idempotencyKey;
}
