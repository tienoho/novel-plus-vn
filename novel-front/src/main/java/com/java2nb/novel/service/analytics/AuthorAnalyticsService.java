package com.java2nb.novel.service.analytics;

import com.java2nb.novel.dto.analytics.ReaderAnalyticsEventInput;

import java.time.LocalDate;

public interface AuthorAnalyticsService {
    void recordReadEvent(ReaderAnalyticsEventInput input);

    AuthorAnalyticsSummary getSummary(long authorId, long bookId, LocalDate startDate, LocalDate endDate);

    AuthorAnalyticsPage getChapterAnalytics(long authorId, long bookId, LocalDate startDate, LocalDate endDate,
                                             int page, int limit);
}
