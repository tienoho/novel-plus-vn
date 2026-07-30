package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

@Data
public class MonthlyRankSnapshotRow {
    private Long id;
    private Long seasonId;
    private Integer sequenceNo;
    private String status;
    private Date cutoffAt;
    private Integer entryCount;
    private Long totalTickets;
    private String contentHash;
    private Date sealedAt;
}
