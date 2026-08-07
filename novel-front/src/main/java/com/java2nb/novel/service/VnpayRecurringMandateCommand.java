package com.java2nb.novel.service;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record VnpayRecurringMandateCommand(String requestId,
                                           String merchantReference,
                                           long userId,
                                           long recurringAmountVnd,
                                           int frequencyMonths,
                                           LocalDate firstRenewalDate,
                                           LocalDateTime merchantDate,
                                           String ipAddress,
                                           String userAgent) {
}
