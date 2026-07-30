package com.java2nb.novel.service.gamification;

import lombok.Data;

@Data
public class MonthlyRankDriftRow {
    private Long seasonId;
    private Long bookId;
    private Long sourceTickets;
    private Long projectedTickets;
    private Long sourceVoters;
    private Long projectedVoters;
}
