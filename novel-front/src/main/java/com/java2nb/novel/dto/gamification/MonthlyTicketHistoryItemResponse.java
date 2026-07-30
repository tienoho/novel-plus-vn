package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.TicketLedgerRow;

import java.util.Date;

public record MonthlyTicketHistoryItemResponse(
    String entryNo,
    String entryType,
    long amount,
    long balanceAfter,
    String businessType,
    Long seasonId,
    Long bookId,
    String reason,
    Date createTime
) {
    public static MonthlyTicketHistoryItemResponse from(TicketLedgerRow row) {
        return new MonthlyTicketHistoryItemResponse(row.getEntryNo(), row.getEntryType(), row.getAmount(),
            row.getBalanceAfter(), row.getBusinessType(), row.getSeasonId(), row.getBookId(),
            row.getReason(), row.getCreateTime());
    }
}
