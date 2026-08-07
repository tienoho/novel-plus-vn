package com.java2nb.novel.service;

public record VnpayRecurringChargeResult(Status status,
                                         String providerTransactionId,
                                         String responseCode) {
    public enum Status {
        SETTLED,
        FINAL_FAILURE,
        PENDING
    }

    public static VnpayRecurringChargeResult settled(String transactionId) {
        return new VnpayRecurringChargeResult(Status.SETTLED, transactionId, "00");
    }

    public static VnpayRecurringChargeResult failed(String responseCode) {
        return new VnpayRecurringChargeResult(Status.FINAL_FAILURE, null, responseCode);
    }

    public static VnpayRecurringChargeResult pending(String responseCode) {
        return new VnpayRecurringChargeResult(Status.PENDING, null, responseCode);
    }
}
