package com.java2nb.novel.dto.subscription;

import com.java2nb.novel.service.VnpayRecurringMandateInitialization;

public record VnpayRecurringMandateResponse(String merchantReference,
                                             String paymentUrl,
                                             String ispTxnId,
                                             String tmnCode,
                                             String dataKey) {
    public static VnpayRecurringMandateResponse from(VnpayRecurringMandateInitialization value) {
        return new VnpayRecurringMandateResponse(value.merchantReference(), value.paymentUrl(),
            value.providerRecurringId(), value.tmnCode(), value.dataKey());
    }
}
