package com.java2nb.novel.service.gamification;

import lombok.Data;

@Data
public class TicketAccountRow {

    private Long id;
    private Long userId;
    private Long availableBalance;
    private Long lifetimeGranted;
    private Long lifetimeSpent;
    private Long lifetimeExpired;
    private Long lifetimeRevoked;
    private String status;
    private Long version;
}
