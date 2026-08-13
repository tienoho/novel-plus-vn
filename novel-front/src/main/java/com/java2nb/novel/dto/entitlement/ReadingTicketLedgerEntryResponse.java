package com.java2nb.novel.dto.entitlement;

import com.java2nb.novel.service.entitlement.ReadingTicketLedgerRow;

public record ReadingTicketLedgerEntryResponse(String entryNo, String entryType, long amount,
                                               long balanceAfter) {
    public static ReadingTicketLedgerEntryResponse from(ReadingTicketLedgerRow row) {
        return new ReadingTicketLedgerEntryResponse(row.getEntryNo(), row.getEntryType(),
            row.getAmount(), row.getBalanceAfter());
    }
}
