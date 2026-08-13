package com.java2nb.novel.dto.entitlement;

import com.java2nb.novel.service.entitlement.ReadingTicketLotPage;

import java.util.List;

public record ReadingTicketLotHistoryResponse(List<ReadingTicketLotEntryResponse> items,
                                               long total, int page, int pageSize) {
    public static ReadingTicketLotHistoryResponse from(ReadingTicketLotPage source) {
        return new ReadingTicketLotHistoryResponse(
            source.items().stream().map(ReadingTicketLotEntryResponse::from).toList(),
            source.total(), source.page(), source.pageSize());
    }
}
