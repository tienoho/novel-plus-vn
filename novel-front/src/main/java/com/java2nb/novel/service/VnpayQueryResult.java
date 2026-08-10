package com.java2nb.novel.service;

public record VnpayQueryResult(Status status, String tradeNo, String responseCode) {

    public VnpayQueryResult(Status status, String tradeNo) {
        this(status, tradeNo, null);
    }

    public enum Status {
        SUCCESS,
        FAILED,
        PENDING,
        UNAVAILABLE
    }
}
