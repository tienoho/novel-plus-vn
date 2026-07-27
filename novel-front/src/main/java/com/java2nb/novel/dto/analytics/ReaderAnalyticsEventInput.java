package com.java2nb.novel.dto.analytics;

import lombok.Data;

@Data
public class ReaderAnalyticsEventInput {
    private String clientEventId;
    private String visitorId;
    private Long bookId;
    private Long indexId;
    private String eventType;
    private Integer progressPercent;
    private Integer durationSeconds;
}
