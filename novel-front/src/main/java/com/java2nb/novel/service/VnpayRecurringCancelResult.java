package com.java2nb.novel.service;

public record VnpayRecurringCancelResult(Status status, String responseCode) {
    public enum Status {
        REVOKED,
        RETRY
    }

    public static VnpayRecurringCancelResult revoked(String responseCode) {
        return new VnpayRecurringCancelResult(Status.REVOKED, responseCode);
    }

    public static VnpayRecurringCancelResult retry(String responseCode) {
        return new VnpayRecurringCancelResult(Status.RETRY, responseCode);
    }
}
