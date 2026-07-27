package com.java2nb.novel.core.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutRequest {
    private String payoutNo;
    private long amountVnd;
    private String bankBin;
    private String bankAccount;
    private String bankAccountName;
    private String description;
}
