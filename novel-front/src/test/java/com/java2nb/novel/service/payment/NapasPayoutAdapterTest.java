package com.java2nb.novel.service.payment;

import com.java2nb.novel.core.payment.PayoutRequest;
import com.java2nb.novel.core.payment.PayoutResult;
import com.java2nb.novel.service.payment.impl.NapasPayoutAdapter;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NapasPayoutAdapterTest {

    @Test
    void testProcessPayoutFailsClosedWithoutProviderContract() {
        NapasPayoutAdapter adapter = new NapasPayoutAdapter();

        PayoutRequest request = PayoutRequest.builder()
            .payoutNo("WD20260725001")
            .amountVnd(500000)
            .bankBin("970422")
            .bankAccount("000111222333")
            .bankAccountName("NGUYEN VAN A")
            .description("CHUYEN KHOAN TAC GIA WD20260725001")
            .build();

        PayoutResult result = adapter.processPayout(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("PROVIDER_NOT_CONFIGURED");
    }

    @Test
    void testProcessPayoutInvalidRequest() {
        NapasPayoutAdapter adapter = new NapasPayoutAdapter();

        PayoutRequest request = PayoutRequest.builder()
            .payoutNo("WD20260725001")
            .amountVnd(0)
            .build();

        PayoutResult result = adapter.processPayout(request);

        assertThat(result.isSuccess()).isFalse();
        assertThat(result.getErrorCode()).isEqualTo("INVALID_PAYOUT_REQUEST");
    }
}
