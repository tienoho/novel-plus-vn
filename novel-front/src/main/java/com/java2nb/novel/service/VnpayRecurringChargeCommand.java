package com.java2nb.novel.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record VnpayRecurringChargeCommand(String requestId,
                                          String orderReference,
                                          String providerRecurringId,
                                          String providerToken,
                                          long amountVnd,
                                          LocalDate recurringDate,
                                          LocalDateTime merchantDate) {
}
