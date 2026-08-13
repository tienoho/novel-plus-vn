package com.java2nb.novel.service;

public class VnpayRecurringUnavailableException extends RuntimeException {
    public VnpayRecurringUnavailableException(String message) {
        super(message);
    }

    public VnpayRecurringUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
