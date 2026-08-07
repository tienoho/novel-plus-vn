package com.java2nb.novel.service.gamification;

public record TicketRiskDecision(long assessmentId, int score, String action, String matchedRules) {
    public boolean blocked() {
        return "BLOCK".equals(action);
    }
}
