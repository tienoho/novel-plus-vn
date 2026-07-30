package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.QuestProgressRow;

import java.util.Date;

public record QuestProgressResponse(String questCode, String name, String periodType,
                                    String periodKey, int currentCount, int targetCount,
                                    boolean completed, boolean claimed, long expReward,
                                    long ticketReward, Date completedAt, Date claimedAt) {
    public static QuestProgressResponse from(QuestProgressRow row, String name) {
        int current = row.getCurrentCount() == null ? 0 : row.getCurrentCount();
        int target = row.getTargetCount() == null ? 0 : row.getTargetCount();
        return new QuestProgressResponse(row.getQuestCode(), name, row.getPeriodType(),
            row.getPeriodKey(), current, target, row.getCompletedAt() != null && current >= target,
            row.getClaimId() != null, row.getExpReward() == null ? 0 : row.getExpReward(),
            row.getTicketReward() == null ? 0 : row.getTicketReward(), row.getCompletedAt(),
            row.getClaimedAt());
    }
}
