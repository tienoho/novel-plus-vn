package com.java2nb.novel.core.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueryOrderResult {
    private String outTradeNo;
    private String bankTradeNo;
    private Integer amountVnd;
    private String status; // SUCCESS, PENDING, FAILED
    private String message;
}
