package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.TicketBookSummary;

public record MonthlyTicketSummaryResponse(
    Long seasonId,
    String periodCode,
    String seasonStatus,
    long totalTickets,
    int maxTicketsPerRequest,
    boolean eligible,
    String ineligibleReason
) {
    public static MonthlyTicketSummaryResponse from(TicketBookSummary source,
                                                    int maxTicketsPerRequest) {
        return new MonthlyTicketSummaryResponse(source.seasonId(), source.periodCode(),
            source.seasonStatus(), source.totalTickets(), maxTicketsPerRequest,
            source.eligible(), source.ineligibleReason());
    }
}
