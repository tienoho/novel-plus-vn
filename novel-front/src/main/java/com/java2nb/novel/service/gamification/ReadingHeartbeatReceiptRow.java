package com.java2nb.novel.service.gamification;

import lombok.Data;

import java.time.LocalDate;
import java.util.Date;

@Data
public class ReadingHeartbeatReceiptRow {
    private Long id;
    private String sessionId;
    private Long userId;
    private Long bookId;
    private Long bookIndexId;
    private Integer sequenceNo;
    private Integer activeSeconds;
    private Integer acceptedSeconds;
    private Integer dailySecondsAfter;
    private Integer firstMinuteBucket;
    private Integer eventCount;
    private LocalDate localDate;
    private String requestHash;
    private Date heartbeatAt;
    private String policyVersion;
}
