package com.java2nb.novel.service.gamification;

public interface TicketRiskService {
    TicketRiskDecision assess(TicketRiskCommand command);

    TicketRiskReviewRow review(long assessmentId, long expectedVersion, String decision,
                               long operatorId, String reason, java.util.Date reviewedAt);
}
