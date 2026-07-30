package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.AuthorRewardAllocationRow;

import java.util.Date;

public record AuthorMonthlyRewardResponse(String allocationNo, long seasonId, String periodCode,
                                          long bookId, String bookName, int rank, long amountXu,
                                          String status, Date postedAt, Date releasedAt,
                                          Date clawedBackAt, String reason) {
    public static AuthorMonthlyRewardResponse from(AuthorRewardAllocationRow row) {
        return new AuthorMonthlyRewardResponse(row.getAllocationNo(), row.getSeasonId(), row.getPeriodCode(),
            row.getBookId(), row.getBookName(), row.getRankNo(), row.getAmountXu(), row.getStatus(),
            row.getPostedAt(), row.getReleasedAt(), row.getClawedBackAt(), row.getReason());
    }
}
