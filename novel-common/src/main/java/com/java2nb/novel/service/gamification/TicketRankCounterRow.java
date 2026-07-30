package com.java2nb.novel.service.gamification;

import lombok.Data;

@Data
public class TicketRankCounterRow {
    private Long seasonId;
    private Long bookId;
    private Long totalTickets;
    private Long voteCount;
    private Long distinctVoterCount;
    private Long version;
}
