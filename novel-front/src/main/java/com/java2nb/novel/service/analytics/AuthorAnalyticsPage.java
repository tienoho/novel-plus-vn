package com.java2nb.novel.service.analytics;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AuthorAnalyticsPage {
    private List<AuthorChapterAnalyticsRow> list;
    private long total;
    private int page;
    private int limit;
}
