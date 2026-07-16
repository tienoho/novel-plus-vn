package com.java2nb.novel.service;

import com.java2nb.novel.core.config.VnpayProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class VnpayServiceTest {

    private VnpayProperties properties;
    private VnpayService service;

    @BeforeEach
    void setUp() {
        properties = new VnpayProperties();
        properties.setEnabled(true);
        properties.setTmnCode("DEMOV210");
        properties.setHashSecret("key");
        properties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("https://merchant.example/pay/vnpay/return");
        service = new VnpayService(properties);
    }

    @Test
    void signsWithHmacSha512() {
        assertThat(service.sign("The quick brown fox jumps over the lazy dog"))
            .isEqualTo("b42af09057bac1e2d41708e48a902e09b5ff7f12ab428a4fe86653c73dd248fb"
                + "82f948a549f7b791a5b41915ee4d1ec3935357e4e2317250d0372afa2ebeeb3a");
    }

    @Test
    void verifiesSortedResponseParametersAndRejectsTampering() {
        Map<String, String> params = new HashMap<>();
        params.put("vnp_TxnRef", "202607160001");
        params.put("vnp_Amount", "1000000");
        params.put("vnp_ResponseCode", "00");
        params.put("vnp_TransactionStatus", "00");
        params.put("vnp_TmnCode", "DEMOV210");
        String signedData = "vnp_Amount=1000000&vnp_ResponseCode=00&vnp_TmnCode=DEMOV210"
            + "&vnp_TransactionStatus=00&vnp_TxnRef=202607160001";
        params.put("vnp_SecureHash", service.sign(signedData));

        assertThat(service.verifySignature(params)).isTrue();

        params.put("vnp_Amount", "50000000");
        assertThat(service.verifySignature(params)).isFalse();

        params.put("vnp_Amount", "1000000");
        params.put("vnp_TmnCode", "MERCHANT2");
        assertThat(service.verifySignature(params)).isFalse();
    }

    @Test
    void validatesConfiguredAmountsAndCalculatesXu() {
        assertThat(properties.isConfigured()).isTrue();
        assertThat(properties.isAllowedAmount(10_000)).isTrue();
        assertThat(properties.isAllowedAmount(11_000)).isFalse();
        assertThat(properties.calculateXu(10_000)).isEqualTo(1_000);

        properties.setXuPerThousandVnd(0);
        assertThat(properties.isConfigured()).isFalse();
        properties.setXuPerThousandVnd(100);
        properties.setReturnUrl("not-a-url");
        assertThat(properties.isConfigured()).isFalse();
    }

    @Test
    void usesThePersistedOrderTimestampForCreateAndExpireDates() {
        Date createTime = Date.from(Instant.parse("2026-07-16T05:00:00Z"));

        String paymentUrl = service.createPaymentUrl(123L, 10_000, "127.0.0.1", createTime);

        assertThat(paymentUrl)
            .contains("vnp_CreateDate=20260716120000")
            .contains("vnp_ExpireDate=20260716121500");
    }
}
