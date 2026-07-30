package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.QuestClaimResult;

public record QuestClaimResponse(String questCode, String periodKey, long expGained,
                                 long ticketGained, long totalExp, int level,
                                 long ticketBalance, boolean alreadyClaimed) {
    public static QuestClaimResponse from(QuestClaimResult result) {
        return new QuestClaimResponse(result.claim().getQuestCode(), result.claim().getPeriodKey(),
            result.claim().getExpAmount(), result.claim().getTicketAmount(),
            result.profile().profile().getTotalExp(), result.profile().profile().getLevel(),
            result.ticketBalance(), result.alreadyClaimed());
    }
}
