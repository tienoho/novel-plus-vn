package com.java2nb.novel.service;

public record VnpayRecurringMandateState(boolean configured,
                                         String status,
                                         String clientRequestId,
                                         String planCode,
                                         Long acceptedPlanVersion) {
}
