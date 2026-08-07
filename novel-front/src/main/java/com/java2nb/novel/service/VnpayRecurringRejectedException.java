package com.java2nb.novel.service;

public class VnpayRecurringRejectedException extends RuntimeException {
    private final String responseCode;

    public VnpayRecurringRejectedException(String responseCode) {
        super("VNPAY Recurring từ chối yêu cầu");
        this.responseCode = responseCode;
    }

    public String getResponseCode() {
        return responseCode;
    }
}
