package com.java2nb.novel.service;

public record VnpayQueryResult(Status status, String tradeNo) {

    public enum Status {
        SUCCESS,
        FAILED,
        PENDING,
        UNAVAILABLE
    }
}
