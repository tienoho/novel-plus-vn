package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

@Data
public class TicketSeasonRow {
    private Long id;
    private String periodCode;
    private String status;
    private Date startAt;
    private Date endAt;
    private Date voteCutoffAt;
    private String policyVersion;
    private Long version;
}
