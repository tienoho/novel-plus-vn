package com.java2nb.novel.service.payment.impl;

import com.java2nb.novel.core.config.VietQrProperties;
import com.java2nb.novel.core.payment.*;
import com.java2nb.novel.core.utils.VietQrGeneratorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@RequiredArgsConstructor
@Slf4j
public class VietQrPaymentAdapter implements PaymentAdapter {

    private static final byte CHANNEL_CODE = 5;
    private static final String CHANNEL_NAME = "VIETQR";
    private static final Pattern ORDER_NO_PATTERN = Pattern.compile("(?:NOVEL|PAY|ORDER)?([0-9]{10,20})", Pattern.CASE_INSENSITIVE);

    private final VietQrProperties vietQrProperties;

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
        if (!vietQrProperties.isConfigured()) {
            return PaymentCreationResult.builder()
                .success(false)
                .errorMessage("Cổng VietQR chưa được cấu hình an toàn")
                .build();
        }
        if (request.getAmountVnd() <= 0) {
            return PaymentCreationResult.builder()
                .success(false)
                .errorMessage("Số tiền nạp không hợp lệ")
                .build();
        }

        String paymentRef = String.valueOf(request.getOutTradeNo());
        String qrCodeData = VietQrGeneratorUtil.generateEmvCoQr(
            vietQrProperties.getBankBin(),
            vietQrProperties.getAccountNo(),
            request.getAmountVnd(),
            paymentRef
        );

        String qrImageUrl = VietQrGeneratorUtil.generateQuickLinkUrl(
            vietQrProperties.getBankBin(),
            vietQrProperties.getAccountNo(),
            request.getAmountVnd(),
            paymentRef,
            vietQrProperties.getAccountName()
        );

        return PaymentCreationResult.builder()
            .success(true)
            .outTradeNo(paymentRef)
            .qrCodeData(qrCodeData)
            .qrImageUrl(qrImageUrl)
            .build();
    }

    @Override
    public WebhookVerifyResult verifyAndParseWebhook(Map<String, String> headers, Map<String, String> params, String body) {
        String tokenHeader = headers != null
            ? headers.getOrDefault("x-vietqr-secret", headers.get("X-VietQR-Secret")) : null;
        boolean isValidSecret = vietQrProperties.isConfigured() && constantTimeEquals(
            vietQrProperties.getSecretToken(), tokenHeader);

        if (!isValidSecret) {
            return WebhookVerifyResult.builder()
                .valid(false)
                .responseCode("401")
                .responseMessage("Invalid VietQR webhook secret token")
                .rawPayload(params)
                .build();
        }

        if (params == null) {
            return invalid("400", "Webhook VietQR thiếu payload");
        }

        String outTradeNo = null;
        outTradeNo = params.get("outTradeNo");
        if (outTradeNo == null) {
            outTradeNo = params.get("orderNo");
        }
        if (outTradeNo == null && params.containsKey("content")) {
            outTradeNo = extractOutTradeNo(params.get("content"));
        }
        if (outTradeNo == null || !outTradeNo.matches("[0-9]{10,20}")) {
            return invalid("400", "Webhook VietQR thiếu mã đơn hợp lệ");
        }

        String bankTradeNo = params.getOrDefault("bankTradeNo", params.get("transactionId"));
        if (bankTradeNo == null || bankTradeNo.isBlank() || bankTradeNo.length() > 128) {
            return invalid("400", "Webhook VietQR thiếu mã giao dịch ngân hàng");
        }

        Integer amountVnd = null;
        if (params.containsKey("amount")) {
            try {
                amountVnd = Integer.parseInt(params.get("amount"));
            } catch (Exception ignored) {}
        }
        if (amountVnd == null || amountVnd <= 0) {
            return invalid("400", "Webhook VietQR thiếu số tiền hợp lệ");
        }
        boolean successful = "SUCCESS".equalsIgnoreCase(params.get("status"))
            || "00".equals(params.get("responseCode"));
        if (!successful) {
            return invalid("409", "Giao dịch VietQR chưa thành công");
        }

        return WebhookVerifyResult.builder()
            .valid(true)
            .outTradeNo(outTradeNo)
            .bankTradeNo(bankTradeNo)
            .amountVnd(amountVnd)
            .successful(successful)
            .responseCode("00")
            .responseMessage("VietQR deposit webhook verified")
            .rawPayload(params)
            .build();
    }

    @Override
    public QueryOrderResult queryOrderStatus(String outTradeNo) {
        return QueryOrderResult.builder()
            .outTradeNo(outTradeNo)
            .status("PENDING")
            .message("Tra cứu VietQR tự động qua sao kê ngân hàng")
            .build();
    }

    @Override
    public PayoutResult processPayout(PayoutRequest request) {
        return PayoutResult.builder()
            .success(false)
            .errorCode("UNSUPPORTED")
            .errorMessage("Sử dụng NAPAS_247 cho giao dịch Payout chi trả")
            .build();
    }

    public static String extractOutTradeNo(String text) {
        if (text == null) return null;
        Matcher matcher = ORDER_NO_PATTERN.matcher(text);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }

    private WebhookVerifyResult invalid(String code, String message) {
        return WebhookVerifyResult.builder()
            .valid(false)
            .successful(false)
            .responseCode(code)
            .responseMessage(message)
            .build();
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
            actual.getBytes(StandardCharsets.UTF_8));
    }
}
