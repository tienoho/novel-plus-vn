package com.java2nb.novel.core.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCreationRequest {
    private long outTradeNo;
    private int amountVnd;
    private long userId;
    private String clientIp;
    private Date createTime;
    private String description;
    private Map<String, Object> extraParams;
}
