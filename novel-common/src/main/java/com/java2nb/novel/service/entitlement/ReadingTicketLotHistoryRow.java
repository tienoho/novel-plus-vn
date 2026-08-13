package com.java2nb.novel.service.entitlement;

import lombok.Data;

import java.util.Date;

@Data
public class ReadingTicketLotHistoryRow {
    private Long id;
    private Long userId;
    private String sourceType;
    private String sourceRef;
    private String grantEntryNo;
    private Long grantedAmount;
    private Long remainingAmount;
    private Date effectiveAt;
    private Date expireAt;
    private String status;
}
