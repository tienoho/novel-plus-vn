package com.java2nb.novel.service;

public record VnpayRecurringMandateInitialization(String merchantReference,
                                                   String providerRecurringId,
                                                   String paymentUrl,
                                                   String tmnCode,
                                                   String dataKey) {
}
