package com.java2nb.novel.service.payment;

import com.java2nb.novel.core.config.VietQrProperties;
import com.java2nb.novel.core.payment.PaymentCreationRequest;
import com.java2nb.novel.core.payment.PaymentCreationResult;
import com.java2nb.novel.core.payment.WebhookVerifyResult;
import com.java2nb.novel.service.payment.impl.VietQrPaymentAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VietQrPaymentAdapterTest {

    private VietQrProperties properties;
    private VietQrPaymentAdapter adapter;

    @BeforeEach
    void setUp() {
        properties = new VietQrProperties();
        properties.setEnabled(true);
        properties.setBankBin("970422");
        properties.setAccountNo("1234567890");
        properties.setAccountName("NOVEL PLUS");
        properties.setSecretToken("0123456789abcdef0123456789abcdef");

        adapter = new VietQrPaymentAdapter(properties);
    }

    @Test
    void testCreateDepositOrder() {
        PaymentCreationRequest request = PaymentCreationRequest.builder()
            .outTradeNo(1002003004L)
            .amountVnd(50000)
            .userId(1L)
            .createTime(new Date())
            .build();

        PaymentCreationResult result = adapter.createDepositOrder(request);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.getOutTradeNo()).isEqualTo("1002003004");
        assertThat(result.getQrCodeData()).contains("A000000727");
        assertThat(result.getQrImageUrl()).contains("970422-1234567890");
    }

    @Test
    void testVerifyAndParseWebhookValid() {
        Map<String, String> headers = Map.of("x-vietqr-secret", "0123456789abcdef0123456789abcdef");
        Map<String, String> params = Map.of(
            "outTradeNo", "1002003004",
            "bankTradeNo", "VQ998877",
            "amount", "50000",
            "status", "SUCCESS"
        );

        WebhookVerifyResult result = adapter.verifyAndParseWebhook(headers, params, null);

        assertThat(result.isValid()).isTrue();
        assertThat(result.getOutTradeNo()).isEqualTo("1002003004");
        assertThat(result.getBankTradeNo()).isEqualTo("VQ998877");
        assertThat(result.getAmountVnd()).isEqualTo(50000);
    }

    @Test
    void testVerifyAndParseWebhookInvalidSecret() {
        Map<String, String> headers = Map.of("x-vietqr-secret", "invalid_secret");
        Map<String, String> params = Map.of("outTradeNo", "1002003004");

        WebhookVerifyResult result = adapter.verifyAndParseWebhook(headers, params, null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("401");
    }

    @Test
    void webhookMissingBankReferenceFailsClosed() {
        Map<String, String> headers = Map.of("x-vietqr-secret", "0123456789abcdef0123456789abcdef");
        Map<String, String> params = Map.of(
            "outTradeNo", "1002003004",
            "amount", "50000",
            "status", "SUCCESS"
        );

        WebhookVerifyResult result = adapter.verifyAndParseWebhook(headers, params, null);

        assertThat(result.isValid()).isFalse();
        assertThat(result.getResponseCode()).isEqualTo("400");
    }

    @Test
    void testExtractOutTradeNoFromContent() {
        String content1 = "Chuyen tien don hang NOVEL1002003004 thanh toan xu";
        String extracted1 = VietQrPaymentAdapter.extractOutTradeNo(content1);
        assertThat(extracted1).isEqualTo("1002003004");

        String content2 = "PAY9876543210 test deposit";
        String extracted2 = VietQrPaymentAdapter.extractOutTradeNo(content2);
        assertThat(extracted2).isEqualTo("9876543210");
    }
}
