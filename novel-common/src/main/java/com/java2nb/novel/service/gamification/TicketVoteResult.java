package com.java2nb.novel.service.gamification;

public record TicketVoteResult(
    TicketPostResult status,
    long voteId,
    long bookTotal,
    long availableBalance
) {
}
