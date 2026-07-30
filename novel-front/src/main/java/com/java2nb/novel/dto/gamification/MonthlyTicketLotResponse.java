package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.TicketLotRow;

import java.util.Date;

public record MonthlyTicketLotResponse(
    long grantedAmount,
    long remainingAmount,
    String sourceType,
    Date effectiveAt,
    Date expireAt
) {
    public static MonthlyTicketLotResponse from(TicketLotRow row) {
        return new MonthlyTicketLotResponse(row.getGrantedAmount(), row.getRemainingAmount(),
            row.getSourceType(), row.getEffectiveAt(), row.getExpireAt());
    }
}
