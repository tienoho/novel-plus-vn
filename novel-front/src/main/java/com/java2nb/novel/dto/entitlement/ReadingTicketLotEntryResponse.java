package com.java2nb.novel.dto.entitlement;

import com.java2nb.novel.service.entitlement.ReadingTicketLotHistoryRow;

import java.util.Date;

public record ReadingTicketLotEntryResponse(String grantEntryNo, String sourceType, long grantedAmount,
                                            long remainingAmount, Date effectiveAt, Date expireAt,
                                            String status) {
    public static ReadingTicketLotEntryResponse from(ReadingTicketLotHistoryRow row) {
        return new ReadingTicketLotEntryResponse(row.getGrantEntryNo(), row.getSourceType(),
            row.getGrantedAmount(), row.getRemainingAmount(), row.getEffectiveAt(), row.getExpireAt(),
            row.getStatus());
    }
}
