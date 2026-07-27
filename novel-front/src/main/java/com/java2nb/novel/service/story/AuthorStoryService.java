package com.java2nb.novel.service.story;

import com.java2nb.novel.dto.author.StoryItemCreateRequest;
import com.java2nb.novel.dto.author.StoryItemUpdateRequest;

import java.util.List;

public interface AuthorStoryService {
    List<AuthorStoryItemRow> list(long authorId, long bookId, String type);

    AuthorStoryItemRow create(long authorId, StoryItemCreateRequest input);

    AuthorStoryItemRow update(long authorId, long itemId, StoryItemUpdateRequest input);

    void delete(long authorId, long itemId, long expectedVersion);
}
