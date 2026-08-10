package com.java2nb.novel.service;

public record VnpayRecurringCancelCommand(String requestId,
                                          String providerRecurringId,
                                          String providerToken,
                                          String ipAddress,
                                          String userAgent) {
}
