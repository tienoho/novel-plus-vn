package com.java2nb.novel.service.analytics;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AuthorChapterAnalyticsRow {
    private Long indexId;
    private String indexName;
    private Integer indexNum;
    private Long readers;
    private Long completions;
    private BigDecimal completionRate;
    private BigDecimal nextChapterRetentionRate;
    private BigDecimal averageProgress;
    private Long averageDurationSeconds;
    private Long purchaseCount;
    private Long buyerCount;
    private Long grossRevenueXu;
    private Long authorRevenueXu;
}
