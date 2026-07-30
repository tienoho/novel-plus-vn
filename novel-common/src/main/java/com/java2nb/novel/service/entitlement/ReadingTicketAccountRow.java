package com.java2nb.novel.service.entitlement;

import lombok.Data;

@Data
public class ReadingTicketAccountRow {
    private Long id;
    private Long userId;
    private Long availableBalance;
    private Long lifetimeGranted;
    private Long lifetimeSpent;
    private Long lifetimeExpired;
    private Long version;
    private String status;
}
