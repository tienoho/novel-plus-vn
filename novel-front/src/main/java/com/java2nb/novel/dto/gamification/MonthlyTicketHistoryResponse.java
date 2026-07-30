package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.TicketHistoryPage;

import java.util.List;

public record MonthlyTicketHistoryResponse(
    List<MonthlyTicketHistoryItemResponse> items,
    long total,
    int page,
    int pageSize
) {
    public static MonthlyTicketHistoryResponse from(TicketHistoryPage source) {
        return new MonthlyTicketHistoryResponse(source.items().stream()
            .map(MonthlyTicketHistoryItemResponse::from).toList(), source.total(), source.page(),
            source.pageSize());
    }
}
