package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

@Data
public class TicketRiskReviewRow {
    private Long assessmentId;
    private String status;
    private Long reviewedBy;
    private String reviewReason;
    private Date reviewedAt;
    private Long version;
}
