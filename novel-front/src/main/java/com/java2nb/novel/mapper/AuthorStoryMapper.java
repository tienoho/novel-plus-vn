package com.java2nb.novel.mapper;

import com.java2nb.novel.service.story.AuthorStoryItemRow;
import org.apache.ibatis.annotations.Param;

import java.util.List;

public interface AuthorStoryMapper {
    List<AuthorStoryItemRow> list(@Param("authorId") long authorId, @Param("bookId") long bookId,
                                  @Param("type") String type);

    AuthorStoryItemRow selectById(@Param("itemId") long itemId);

    AuthorStoryItemRow selectOwned(@Param("authorId") long authorId, @Param("itemId") long itemId);

    int insert(AuthorStoryItemRow item);

    int updateOwned(@Param("authorId") long authorId, @Param("itemId") long itemId,
                    @Param("type") String type, @Param("title") String title,
                    @Param("content") String content, @Param("timelineLabel") String timelineLabel,
                    @Param("sortOrder") int sortOrder, @Param("expectedVersion") long expectedVersion);

    int deleteOwned(@Param("authorId") long authorId, @Param("itemId") long itemId,
                    @Param("expectedVersion") long expectedVersion);
}
