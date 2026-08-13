package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.MonthlyRankingPage;

import java.util.Date;
import java.util.List;

public record MonthlyTicketRankingResponse(long seasonId, String periodCode, String seasonStatus,
                                           Date cutoffAt, boolean snapshot,
                                           List<MonthlyTicketRankingEntryResponse> entries,
                                           long total, int page, int pageSize) {
    public static MonthlyTicketRankingResponse from(MonthlyRankingPage source) {
        return new MonthlyTicketRankingResponse(source.seasonId(), source.periodCode(),
            source.seasonStatus(), source.cutoffAt(), source.snapshot(), source.entries().stream()
                .map(MonthlyTicketRankingEntryResponse::from).toList(), source.total(),
            source.page(), source.pageSize());
    }
}
