package com.java2nb.novel.service;

import com.java2nb.novel.dto.author.DraftAutosaveRequest;
import com.java2nb.novel.entity.AuthorChapterDraft;

import java.util.Date;
import java.util.List;

public interface AuthorChapterDraftService {
    AuthorChapterDraft autosave(long authorId, DraftAutosaveRequest request);

    AuthorChapterDraft get(long authorId, long draftId);

    List<AuthorChapterDraft> list(long authorId, String status, int page, int pageSize);

    long count(long authorId, String status);

    AuthorChapterDraft schedule(long authorId, long draftId, long expectedVersion, Date scheduledAt);

    AuthorChapterDraft cancelSchedule(long authorId, long draftId, long expectedVersion);

    AuthorChapterDraft publishNow(long authorId, long draftId, long expectedVersion);

    List<AuthorChapterDraft> listDue(Date now, int limit);

    AuthorChapterDraft publishScheduled(long draftId, Date now);

    void recordScheduleFailure(long draftId, String message);
}
