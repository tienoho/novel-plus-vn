package com.java2nb.novel.service;

import com.java2nb.novel.core.config.VnpayRecurringProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class VnpayRecurringSignerTest {

    private VnpayRecurringSigner signer;

    @BeforeEach
    void setUp() {
        VnpayRecurringProperties properties = new VnpayRecurringProperties();
        properties.setHashSecret("0123456789abcdef0123456789abcdef");
        signer = new VnpayRecurringSigner(properties);
    }

    @Test
    void signsMandateFieldsInDocumentedOrder() {
        List<Object> fields = List.of(
            "17000000000000001", "recurring", "NP123", "Dang ky gia han goi doc Novel Plus",
            "other", "VNPAYREC", 10_000_000L, 1, "month", 0, "20260901", "20260901",
            "99991231", 0, "VND", "", 123, "", "", "127.0.0.1", "NovelPlusWeb",
            "https://example.com/return", "https://example.com/cancel", "2.1.0", "vn",
            "20260801120000");

        assertThat(signer.signFields(fields)).isEqualTo(
            "0f9e1ff5574c1b756da5c9917f42d1acfacb945327f2a17f55f234afac03bee8"
                + "e1d452a46b6802ad825d12c8c1faf8b9b647698b394445bb60dbe62f0915ccee");
    }

    @Test
    void verifiesSortedCallbackAndRejectsTampering() {
        Map<String, String> parameters = callbackParameters();
        parameters.put("vnp_secure_hash",
            "3e81597d478ff02b1f35c934abc97044c89cafea90e5356ab589b0fa4f91e0742"
                + "48c7d292e3eb3443d53afaac19993bbd3906f1b50f4159106e213262a94815b");

        assertThat(signer.verifyCallback(parameters)).isTrue();

        parameters.put("vnp_app_user_id", "124");
        assertThat(signer.verifyCallback(parameters)).isFalse();
    }

    private Map<String, String> callbackParameters() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("vnp_tmn_code", "VNPAYREC");
        values.put("vnp_app_user_id", "123");
        values.put("vnp_token", "tokenABC123");
        values.put("vnp_token_exp_date", "20271231");
        values.put("vnp_command", "recurring");
        values.put("vnp_txn_ref", "NP123");
        values.put("vnp_response_code", "00");
        values.put("vnp_transaction_status", "00");
        values.put("vnp_pay_date", "20260801123000");
        return values;
    }
}
