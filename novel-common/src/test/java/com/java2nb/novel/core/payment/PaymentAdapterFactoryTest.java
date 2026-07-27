package com.java2nb.novel.core.payment;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PaymentAdapterFactoryTest {

    static class DummyPaymentAdapter implements PaymentAdapter {
        private final byte code;
        private final String name;

        DummyPaymentAdapter(byte code, String name) {
            this.code = code;
            this.name = name;
        }

        @Override public byte getChannelCode() { return code; }
        @Override public String getChannelName() { return name; }
        @Override public PaymentCreationResult createDepositOrder(PaymentCreationRequest request) { return null; }
        @Override public WebhookVerifyResult verifyAndParseWebhook(Map<String, String> headers, Map<String, String> params, String body) { return null; }
        @Override public QueryOrderResult queryOrderStatus(String outTradeNo) { return null; }
        @Override public PayoutResult processPayout(PayoutRequest request) { return null; }
    }

    @Test
    void registersAndRoutesAdaptersByCodeAndName() {
        PaymentAdapter vnpayAdapter = new DummyPaymentAdapter((byte) 4, "VNPAY");
        PaymentAdapter vietqrAdapter = new DummyPaymentAdapter((byte) 5, "VIETQR");

        PaymentAdapterFactory factory = new PaymentAdapterFactory(List.of(vnpayAdapter, vietqrAdapter));

        assertThat(factory.getAdapter((byte) 4)).isEqualTo(vnpayAdapter);
        assertThat(factory.getAdapter((byte) 5)).isEqualTo(vietqrAdapter);
        assertThat(factory.getAdapter("VNPAY")).isEqualTo(vnpayAdapter);
        assertThat(factory.getAdapter("vietqr")).isEqualTo(vietqrAdapter);

        assertThatThrownBy(() -> factory.getAdapter((byte) 99))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("99");
    }
}
