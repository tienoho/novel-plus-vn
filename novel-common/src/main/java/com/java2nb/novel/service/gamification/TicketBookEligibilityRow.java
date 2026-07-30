package com.java2nb.novel.service.gamification;

import lombok.Data;

@Data
public class TicketBookEligibilityRow {
    private Long bookId;
    private Long authorId;
    private Long ownerUserId;
    private Byte status;
    private Byte auditStatus;
    private Integer crawlSourceId;
    private String crawlBookId;
    private Boolean blocked;
    private Boolean selfOwned;
    private Boolean collaborator;
}
