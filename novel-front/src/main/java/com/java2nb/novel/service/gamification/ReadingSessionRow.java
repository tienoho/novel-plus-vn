package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.util.Date;

@Data
public class ReadingSessionRow {
    private String sessionId;
    private Long userId;
    private Long bookId;
    private Long bookIndexId;
    private Integer lastSequence;
    private Date lastHeartbeatAt;
    private Integer totalAcceptedSeconds;
    private String status;
    private Long version;
}
