package com.java2nb.novel.service.impl;

import com.java2nb.novel.dto.author.DraftAutosaveRequest;
import com.java2nb.novel.entity.AuthorChapterDraft;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.mapper.AuthorChapterDraftMapper;
import com.java2nb.novel.mapper.BookIndexMapper;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.collaboration.AuthorBookAccess;
import com.java2nb.novel.service.collaboration.AuthorBookCollaborationService;
import com.java2nb.novel.service.collaboration.BookPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class AuthorChapterDraftServiceImplTest {
    private AuthorChapterDraftMapper draftMapper;
    private BookIndexMapper bookIndexMapper;
    private BookService bookService;
    private AuthorBookCollaborationService collaborationService;
    private AuthorChapterDraftServiceImpl service;

    @BeforeEach
    void setUp() {
        draftMapper = mock(AuthorChapterDraftMapper.class);
        bookIndexMapper = mock(BookIndexMapper.class);
        bookService = mock(BookService.class);
        collaborationService = mock(AuthorBookCollaborationService.class);
        service = new AuthorChapterDraftServiceImpl(
            draftMapper, bookIndexMapper, bookService, collaborationService);
        AuthorBookAccess access = new AuthorBookAccess();
        access.setBookId(10L);
        access.setOwnerAuthorId(7L);
        access.setActorAuthorId(7L);
        access.setOwner(true);
        when(collaborationService.requirePermission(anyLong(), anyLong(), any()))
            .thenReturn(access);
        Book book = new Book();
        book.setId(10L);
        book.setAuthorId(7L);
        when(bookService.queryBookDetail(10L)).thenReturn(book);
        when(draftMapper.insertEvent(anyLong(), anyString(), anyString(), nullable(String.class), anyString(),
            anyString(), nullable(Long.class), nullable(String.class))).thenReturn(1);
    }

    @Test
    void editorCanAutosaveButCannotPublishWithoutPublishPermission() {
        DraftAutosaveRequest input = input(null, null);
        AuthorChapterDraft stored = draft(100L, "DRAFT", 0L);
        when(draftMapper.selectByClientKey(7L, "editor_key_123")).thenReturn(null);
        when(draftMapper.insert(any())).thenAnswer(invocation -> {
            AuthorChapterDraft inserted = invocation.getArgument(0);
            inserted.setId(100L);
            return 1;
        });
        when(draftMapper.selectById(100L)).thenReturn(stored);

        assertThat(service.autosave(7L, input)).isSameAs(stored);
        when(collaborationService.requirePermission(7L, 10L, BookPermission.PUBLISH_CHAPTERS))
            .thenThrow(new com.java2nb.novel.core.exception.BusinessException(
                com.java2nb.novel.core.enums.ResponseStatus.AUTHOR_BOOK_ACCESS_FORBIDDEN));

        assertThatThrownBy(() -> service.publishNow(7L, 100L, 0L))
            .isInstanceOf(com.java2nb.novel.core.exception.BusinessException.class);
        verify(bookService, never()).addBookContent(anyLong(), anyString(), anyString(), anyByte(), anyLong());
    }

    @Test
    void createRetryUsesClientKeyWithoutCreatingSecondDraft() {
        DraftAutosaveRequest input = input(null, null);
        AuthorChapterDraft stored = draft(100L, "DRAFT", 0L);
        when(draftMapper.selectByClientKey(7L, "editor_key_123")).thenReturn(null, stored);
        when(draftMapper.insert(any())).thenAnswer(invocation -> {
            AuthorChapterDraft inserted = invocation.getArgument(0);
            inserted.setId(100L);
            return 1;
        });
        when(draftMapper.selectById(100L)).thenReturn(stored);

        AuthorChapterDraft created = service.autosave(7L, input);
        AuthorChapterDraft retried = service.autosave(7L, input);

        assertThat(created.getId()).isEqualTo(100L);
        assertThat(retried.getId()).isEqualTo(100L);
        verify(draftMapper, times(1)).insert(any());
        verify(draftMapper, times(1)).insertEvent(eq(100L), anyString(), eq("DRAFT_CREATED"), isNull(),
            eq("DRAFT"), eq("AUTHOR"), eq(7L), isNull());
    }

    @Test
    void staleAutosaveVersionIsRejected() {
        AuthorChapterDraft current = draft(100L, "DRAFT", 2L);
        when(draftMapper.selectById(100L)).thenReturn(current);
        when(draftMapper.updateAutosave(eq(100L), eq(7L), eq(1L), anyString(), anyString(), anyByte(), any()))
            .thenReturn(0);

        DraftAutosaveRequest input = input(100L, 1L);

        assertThatThrownBy(() -> service.autosave(7L, input))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("phiên khác");
    }

    @Test
    void publishClaimsDraftAndCommitsPublishedIndex() {
        AuthorChapterDraft current = draft(100L, "DRAFT", 3L);
        AuthorChapterDraft published = draft(100L, "PUBLISHED", 5L);
        published.setPublishedIndexId(99L);
        when(draftMapper.selectById(100L)).thenReturn(current, published);
        when(draftMapper.claimForPublishing(100L, 7L, 3L, "DRAFT")).thenReturn(1);
        when(bookService.addBookContent(eq(10L), eq("Chương 1"), anyString(), eq((byte) 0), eq(7L)))
            .thenReturn(99L);
        when(draftMapper.markPublished(100L, 7L, 4L, 99L)).thenReturn(1);

        AuthorChapterDraft result = service.publishNow(7L, 100L, 3L);

        assertThat(result.getStatus()).isEqualTo("PUBLISHED");
        verify(bookService).addBookContent(10L, "Chương 1", "Nội&nbsp;dung", (byte) 0, 7L);
        verify(draftMapper).insertEvent(100L, "DR-100", "DRAFT_PUBLISHED", "DRAFT", "PUBLISHED",
            "AUTHOR", 7L, "Chương đã xuất bản: 99");
    }

    @Test
    void scheduleRequiresFutureTimeAndOptimisticVersion() {
        AuthorChapterDraft current = draft(100L, "DRAFT", 2L);
        AuthorChapterDraft scheduled = draft(100L, "SCHEDULED", 3L);
        when(draftMapper.selectById(100L)).thenReturn(current, scheduled);
        Date future = new Date(System.currentTimeMillis() + 120_000L);
        when(draftMapper.schedule(100L, 7L, 2L, future)).thenReturn(1);

        AuthorChapterDraft result = service.schedule(7L, 100L, 2L, future);

        assertThat(result.getStatus()).isEqualTo("SCHEDULED");
        verify(draftMapper).insertEvent(eq(100L), eq("DR-100"), eq("DRAFT_SCHEDULED"), eq("DRAFT"),
            eq("SCHEDULED"), eq("AUTHOR"), eq(7L), contains(Long.toString(future.getTime())));
    }

    @Test
    void authorCannotReadAnotherAuthorsDraft() {
        AuthorChapterDraft current = draft(100L, "DRAFT", 0L);
        when(draftMapper.selectById(100L)).thenReturn(current);

        assertThatThrownBy(() -> service.get(8L, 100L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("tác giả");
    }

    private DraftAutosaveRequest input(Long draftId, Long expectedVersion) {
        DraftAutosaveRequest input = new DraftAutosaveRequest();
        input.setDraftId(draftId);
        input.setClientKey("editor_key_123");
        input.setBookId(10L);
        input.setIndexName("Chương 1");
        input.setContent("Nội dung");
        input.setIsVip((byte) 0);
        input.setExpectedVersion(expectedVersion);
        return input;
    }

    private AuthorChapterDraft draft(long id, String status, long version) {
        AuthorChapterDraft draft = new AuthorChapterDraft();
        draft.setId(id);
        draft.setDraftNo("DR-100");
        draft.setClientKey("editor_key_123");
        draft.setAuthorId(7L);
        draft.setBookId(10L);
        draft.setIndexName("Chương 1");
        draft.setContent("Nội dung");
        draft.setIsVip((byte) 0);
        draft.setStatus(status);
        draft.setVersion(version);
        return draft;
    }
}
