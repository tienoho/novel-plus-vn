package com.java2nb.novel.service.gamification;

public record QuestClaimResult(QuestClaimRow claim, GamificationProfileSnapshot profile,
                               long ticketBalance, boolean alreadyClaimed) {
}
