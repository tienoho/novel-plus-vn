package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.TickerEntryRow;

public record TickerEntryResponse(String nickname, String bookName, long ticketCount) {
    public static TickerEntryResponse from(TickerEntryRow row) {
        return new TickerEntryResponse(row.getNickname(), row.getBookName(),
            row.getTicketCount() == null ? 0 : row.getTicketCount());
    }
}
