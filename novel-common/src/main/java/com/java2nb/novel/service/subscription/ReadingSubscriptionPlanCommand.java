package com.java2nb.novel.service.subscription;

import java.util.Locale;

public record ReadingSubscriptionPlanCommand(String planCode, String planName,
                                             long priceVnd, Long priceXu,
                                             long ticketsPerPeriod, int periodMonths,
                                             int ticketValidityDays) {
    public ReadingSubscriptionPlanCommand(String planCode, String planName, long priceVnd,
                                          long ticketsPerPeriod, int periodMonths,
                                          int ticketValidityDays) {
        this(planCode, planName, priceVnd, null, ticketsPerPeriod, periodMonths,
            ticketValidityDays);
    }

    public ReadingSubscriptionPlanCommand {
        planCode = planCode == null ? "" : planCode.trim().toUpperCase(Locale.ROOT);
        planName = planName == null ? "" : planName.trim();
        if (!planCode.matches("[A-Z0-9_]{3,32}")
            || planName.length() < 3 || planName.length() > 100
            || priceVnd < 1_000 || priceVnd > 100_000_000
            || (priceXu != null && (priceXu < 1 || priceXu > 100_000_000))
            || ticketsPerPeriod < 1 || ticketsPerPeriod > 100_000
            || periodMonths < 1 || periodMonths > 12
            || ticketValidityDays < 1 || ticketValidityDays > 366) {
            throw new IllegalArgumentException("Cấu hình gói thuê bao không hợp lệ");
        }
    }
}
