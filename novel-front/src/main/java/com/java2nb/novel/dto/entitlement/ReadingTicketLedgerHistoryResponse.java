package com.java2nb.novel.dto.entitlement;

import com.java2nb.novel.service.entitlement.ReadingTicketLedgerPage;

import java.util.List;

public record ReadingTicketLedgerHistoryResponse(List<ReadingTicketLedgerEntryResponse> items,
                                                  long total, int page, int pageSize) {
    public static ReadingTicketLedgerHistoryResponse from(ReadingTicketLedgerPage source) {
        return new ReadingTicketLedgerHistoryResponse(
            source.items().stream().map(ReadingTicketLedgerEntryResponse::from).toList(),
            source.total(), source.page(), source.pageSize());
    }
}
