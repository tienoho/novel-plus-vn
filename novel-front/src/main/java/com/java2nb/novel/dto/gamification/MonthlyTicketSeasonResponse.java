package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.MonthlySeasonRow;

import java.util.Date;

public record MonthlyTicketSeasonResponse(
    long seasonId,
    String periodCode,
    String seasonType,
    Date startAt,
    Date endAt,
    Date voteCutoffAt,
    String status
) {
    public static MonthlyTicketSeasonResponse from(MonthlySeasonRow source) {
        return new MonthlyTicketSeasonResponse(source.getId(), source.getPeriodCode(),
            source.getSeasonType(), source.getStartAt(), source.getEndAt(),
            source.getVoteCutoffAt(), source.getStatus());
    }
}
