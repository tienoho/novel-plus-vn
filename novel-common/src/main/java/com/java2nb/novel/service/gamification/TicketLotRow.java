package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

@Data
public class TicketLotRow {

    private Long id;
    private Long userId;
    private String sourceType;
    private String sourceRef;
    private Long grantedAmount;
    private Long remainingAmount;
    private Long grantLedgerId;
    private Date effectiveAt;
    private Date expireAt;
    private String status;
    private Long version;
}
