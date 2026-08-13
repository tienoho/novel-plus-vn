package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.MonthlyRankRow;

public record MonthlyTicketRankingEntryResponse(int rank, long bookId, Long authorId,
                                                String bookName, String authorName, String picUrl,
                                                long totalTickets, long distinctVoterCount) {
    public static MonthlyTicketRankingEntryResponse from(MonthlyRankRow row) {
        return new MonthlyTicketRankingEntryResponse(row.getRankNo(), row.getBookId(),
            row.getAuthorId(), row.getBookName(), row.getAuthorName(), row.getPicUrl(),
            row.getTotalTickets(), row.getDistinctVoterCount());
    }
}
