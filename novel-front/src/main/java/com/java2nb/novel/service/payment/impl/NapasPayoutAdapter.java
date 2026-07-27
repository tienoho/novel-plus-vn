package com.java2nb.novel.service.payment.impl;

import com.java2nb.novel.core.payment.*;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class NapasPayoutAdapter implements PaymentAdapter {

    private static final byte CHANNEL_CODE = 6;
    private static final String CHANNEL_NAME = "NAPAS_247";

    @Override
    public byte getChannelCode() {
        return CHANNEL_CODE;
    }

    @Override
    public String getChannelName() {
        return CHANNEL_NAME;
    }

    @Override
    public PaymentCreationResult createDepositOrder(PaymentCreationRequest request) {
        return PaymentCreationResult.builder()
            .success(false)
            .errorMessage("NAPAS_247 chỉ dành riêng cho cổng chuyển khoản chi trả (Payout)")
            .build();
    }

    @Override
    public WebhookVerifyResult verifyAndParseWebhook(Map<String, String> headers, Map<String, String> params, String body) {
        return WebhookVerifyResult.builder()
            .valid(false)
            .responseCode("99")
            .responseMessage("Không hỗ trợ nạp tiền qua NAPAS_247 Direct Webhook")
            .build();
    }

    @Override
    public QueryOrderResult queryOrderStatus(String outTradeNo) {
        return QueryOrderResult.builder()
            .outTradeNo(outTradeNo)
            .status("UNSUPPORTED")
            .message("Chưa cấu hình API đối tác ngân hàng/NAPAS")
            .build();
    }

    @Override
    public PayoutResult processPayout(PayoutRequest request) {
        if (request == null || request.getPayoutNo() == null || request.getAmountVnd() <= 0) {
            return PayoutResult.builder()
                .success(false)
                .errorCode("INVALID_PAYOUT_REQUEST")
                .errorMessage("Thông tin chuyển khoản không hợp lệ")
                .build();
        }
        if (request.getBankAccount() == null || request.getBankAccount().trim().isEmpty()) {
            return PayoutResult.builder()
                .success(false)
                .errorCode("MISSING_BANK_ACCOUNT")
                .errorMessage("Thiếu số tài khoản nhận tiền")
                .build();
        }

        return PayoutResult.builder()
            .success(false)
            .errorCode("PROVIDER_NOT_CONFIGURED")
            .errorMessage("Chưa có hợp đồng API payout với ngân hàng/đối tác NAPAS")
            .build();
    }
}
