package com.java2nb.novel.service.gamification;

public record TicketBookSummary(
    Long seasonId,
    String periodCode,
    String seasonStatus,
    long totalTickets,
    boolean eligible,
    String ineligibleReason
) {

    public TicketBookSummary withEligibility(boolean allowed, String reason) {
        return new TicketBookSummary(seasonId, periodCode, seasonStatus, totalTickets, allowed, reason);
    }
}
