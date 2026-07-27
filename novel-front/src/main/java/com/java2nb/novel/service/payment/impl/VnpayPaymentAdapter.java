package com.java2nb.novel.service.payment.impl;

import com.java2nb.novel.core.config.VnpayProperties;
import com.java2nb.novel.core.payment.*;
import com.java2nb.novel.service.VnpayService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@RequiredArgsConstructor
public class VnpayPaymentAdapter implements PaymentAdapter {

    private static final byte CHANNEL_CODE = 4;
    private static final String CHANNEL_NAME = "VNPAY";

    private final VnpayProperties vnpayProperties;
    private final VnpayService vnpayService;

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
        if (!vnpayProperties.isConfigured()) {
            return PaymentCreationResult.builder()
                .success(false)
                .errorMessage("Cổng thanh toán VNPAY chưa được cấu hình")
                .build();
        }
        if (!vnpayProperties.isAllowedAmount(request.getAmountVnd())) {
            return PaymentCreationResult.builder()
                .success(false)
                .errorMessage("Số tiền nạp không hợp lệ")
                .build();
        }

        String url = vnpayService.createPaymentUrl(
            request.getOutTradeNo(),
            request.getAmountVnd(),
            request.getClientIp(),
            request.getCreateTime()
        );

        return PaymentCreationResult.builder()
            .success(true)
            .outTradeNo(String.valueOf(request.getOutTradeNo()))
            .paymentUrl(url)
            .build();
    }

    @Override
    public WebhookVerifyResult verifyAndParseWebhook(Map<String, String> headers, Map<String, String> params, String body) {
        boolean valid = vnpayService.verifySignature(params);
        if (!valid) {
            return WebhookVerifyResult.builder()
                .valid(false)
                .responseCode("97")
                .responseMessage("Invalid checksum")
                .rawPayload(params)
                .build();
        }

        String outTradeNo = params.get("vnp_TxnRef");
        String bankTradeNo = params.get("vnp_TransactionNo");
        String rawAmount = params.get("vnp_Amount");
        Integer amountVnd = null;
        if (rawAmount != null) {
            try {
                long amountL = Long.parseLong(rawAmount);
                if (amountL > 0 && amountL % 100 == 0) {
                    amountVnd = (int) (amountL / 100);
                }
            } catch (Exception ignored) {}
        }

        boolean successful = "00".equals(params.get("vnp_ResponseCode"))
            && "00".equals(params.get("vnp_TransactionStatus"));

        return WebhookVerifyResult.builder()
            .valid(true)
            .outTradeNo(outTradeNo)
            .bankTradeNo(bankTradeNo)
            .amountVnd(amountVnd)
            .successful(successful)
            .responseCode(params.get("vnp_ResponseCode"))
            .responseMessage(successful ? "Confirm success" : "Payment failed")
            .rawPayload(params)
            .build();
    }

    @Override
    public QueryOrderResult queryOrderStatus(String outTradeNo) {
        return QueryOrderResult.builder()
            .outTradeNo(outTradeNo)
            .status("PENDING")
            .message("Tra cứu trực tiếp chưa hỗ trợ")
            .build();
    }

    @Override
    public PayoutResult processPayout(PayoutRequest request) {
        return PayoutResult.builder()
            .success(false)
            .errorCode("UNSUPPORTED")
            .errorMessage("VNPAY không hỗ trợ trực tiếp Payout")
            .build();
    }
}
