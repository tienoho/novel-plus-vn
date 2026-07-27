package com.java2nb.novel.mapper;

import com.java2nb.novel.entity.AuthorChapterDraft;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

@Mapper
public interface AuthorChapterDraftMapper {
    int insert(AuthorChapterDraft draft);

    AuthorChapterDraft selectById(@Param("id") long id);

    AuthorChapterDraft selectByClientKey(@Param("authorId") long authorId,
                                         @Param("clientKey") String clientKey);

    List<AuthorChapterDraft> listByAuthor(@Param("authorId") long authorId,
                                          @Param("status") String status,
                                          @Param("offset") long offset,
                                          @Param("limit") int limit);

    long countByAuthor(@Param("authorId") long authorId, @Param("status") String status);

    List<AuthorChapterDraft> listDueScheduled(@Param("now") Date now, @Param("limit") int limit);

    int updateAutosave(@Param("id") long id,
                       @Param("authorId") long authorId,
                       @Param("expectedVersion") long expectedVersion,
                       @Param("indexName") String indexName,
                       @Param("content") String content,
                       @Param("isVip") byte isVip,
                       @Param("now") Date now);

    int schedule(@Param("id") long id,
                 @Param("authorId") long authorId,
                 @Param("expectedVersion") long expectedVersion,
                 @Param("scheduledAt") Date scheduledAt);

    int cancelSchedule(@Param("id") long id,
                       @Param("authorId") long authorId,
                       @Param("expectedVersion") long expectedVersion);

    int claimForPublishing(@Param("id") long id,
                           @Param("authorId") long authorId,
                           @Param("expectedVersion") long expectedVersion,
                           @Param("fromStatus") String fromStatus);

    int markPublished(@Param("id") long id,
                      @Param("authorId") long authorId,
                      @Param("expectedVersion") long expectedVersion,
                      @Param("publishedIndexId") long publishedIndexId);

    int recordLastError(@Param("id") long id, @Param("message") String message);

    int insertEvent(@Param("draftId") long draftId,
                    @Param("draftNo") String draftNo,
                    @Param("eventType") String eventType,
                    @Param("fromStatus") String fromStatus,
                    @Param("toStatus") String toStatus,
                    @Param("actorType") String actorType,
                    @Param("actorId") Long actorId,
                    @Param("detail") String detail);
}
