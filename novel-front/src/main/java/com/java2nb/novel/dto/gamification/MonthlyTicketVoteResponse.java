package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.TicketVoteResult;

public record MonthlyTicketVoteResponse(
    String status,
    long voteId,
    long bookTotal,
    long availableBalance
) {
    public static MonthlyTicketVoteResponse from(TicketVoteResult source) {
        return new MonthlyTicketVoteResponse(source.status().name(), source.voteId(), source.bookTotal(),
            source.availableBalance());
    }
}
