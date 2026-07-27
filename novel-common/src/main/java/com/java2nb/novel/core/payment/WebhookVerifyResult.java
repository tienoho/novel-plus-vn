package com.java2nb.novel.core.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WebhookVerifyResult {
    private boolean valid;
    private String outTradeNo;
    private String bankTradeNo;
    private Integer amountVnd;
    private boolean successful;
    private String responseCode;
    private String responseMessage;
    private Map<String, String> rawPayload;
}
