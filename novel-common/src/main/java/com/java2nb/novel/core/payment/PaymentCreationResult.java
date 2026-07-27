package com.java2nb.novel.core.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PaymentCreationResult {
    private boolean success;
    private String outTradeNo;
    private String paymentUrl;
    private String qrCodeData;
    private String qrImageUrl;
    private String errorMessage;
}
