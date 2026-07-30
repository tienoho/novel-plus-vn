package com.java2nb.novel.dto.gamification;

import java.util.List;

public record MonthlyTicketAccountResponse(
    long availableBalance,
    List<MonthlyTicketLotResponse> expiringLots
) {
}
