package com.java2nb.novel.service.analytics;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class AuthorAnalyticsSummary {
    private Long bookId;
    private String bookName;
    private Long uniqueReaders;
    private Long chapterStarts;
    private Long chapterCompletions;
    private BigDecimal completionRate;
    private BigDecimal nextChapterRetentionRate;
    private Long readingSeconds;
    private Long purchaseCount;
    private Long buyerCount;
    private Long grossRevenueXu;
    private Long authorRevenueXu;
}
