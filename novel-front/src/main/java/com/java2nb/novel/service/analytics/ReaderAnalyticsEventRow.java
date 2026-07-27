package com.java2nb.novel.service.analytics;

import lombok.Data;

@Data
public class ReaderAnalyticsEventRow {
    private String clientEventId;
    private String readerKeyHash;
    private Long bookId;
    private Long indexId;
    private String eventType;
    private Integer progressPercent;
    private Integer durationSeconds;
}
