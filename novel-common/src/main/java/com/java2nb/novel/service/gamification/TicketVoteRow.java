package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

@Data
public class TicketVoteRow {
    private Long id;
    private Long seasonId;
    private Long bookId;
    private Long authorId;
    private Long userId;
    private Long ticketCount;
    private Long ledgerId;
    private String idempotencyKey;
    private String requestHash;
    private String clientRequestId;
    private String status;
    private Date createTime;
}
