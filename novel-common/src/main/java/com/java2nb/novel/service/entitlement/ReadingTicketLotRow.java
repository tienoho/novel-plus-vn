package com.java2nb.novel.service.entitlement;

import lombok.Data;

import java.util.Date;

@Data
public class ReadingTicketLotRow {
    private Long id;
    private Long userId;
    private Long remainingAmount;
    private Date effectiveAt;
    private Date expireAt;
    private String status;
    private Long version;
}
