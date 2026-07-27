package com.java2nb.novel.mapper;

import com.java2nb.novel.service.analytics.AuthorAnalyticsSummary;
import com.java2nb.novel.service.analytics.AuthorChapterAnalyticsRow;
import com.java2nb.novel.service.analytics.ReaderAnalyticsEventRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface ReaderAnalyticsMapper {
    int insertEvent(ReaderAnalyticsEventRow event);

    AuthorAnalyticsSummary selectSummary(@Param("bookId") long bookId,
                                         @Param("authorId") long authorId,
                                         @Param("startTime") Date startTime,
                                         @Param("endTime") Date endTime);

    List<AuthorChapterAnalyticsRow> selectChapterAnalytics(@Param("bookId") long bookId,
                                                            @Param("authorId") long authorId,
                                                            @Param("startTime") Date startTime,
                                                            @Param("endTime") Date endTime,
                                                            @Param("offset") int offset,
                                                            @Param("limit") int limit);

    long countChapters(@Param("bookId") long bookId);
}
