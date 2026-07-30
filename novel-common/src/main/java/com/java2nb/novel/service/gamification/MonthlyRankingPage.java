package com.java2nb.novel.service.gamification;

import java.util.Date;
import java.util.List;

/** Trang xếp hạng công khai; {@code snapshot=true} nghĩa là kết quả đã được chụp bất biến. */
public record MonthlyRankingPage(long seasonId, String periodCode, String seasonStatus,
                                 Date cutoffAt, boolean snapshot, List<MonthlyRankRow> entries,
                                 long total, int page, int pageSize) {
    public MonthlyRankingPage {
        cutoffAt = cutoffAt == null ? null : new Date(cutoffAt.getTime());
        entries = List.copyOf(entries);
    }

    @Override
    public Date cutoffAt() {
        return cutoffAt == null ? null : new Date(cutoffAt.getTime());
    }
}
