package com.java2nb.novel.service.analytics;

import com.java2nb.novel.core.utils.ContentHashUtil;
import com.java2nb.novel.dto.analytics.ReaderAnalyticsEventInput;
import com.java2nb.novel.entity.Book;
import com.java2nb.novel.entity.BookIndex;
import com.java2nb.novel.mapper.BookIndexMapper;
import com.java2nb.novel.mapper.FrontBookMapper;
import com.java2nb.novel.mapper.ReaderAnalyticsMapper;
import com.java2nb.novel.service.collaboration.AuthorBookAccess;
import com.java2nb.novel.service.collaboration.AuthorBookCollaborationService;
import com.java2nb.novel.service.collaboration.BookPermission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.*;

class AuthorAnalyticsServiceImplTest {
    private ReaderAnalyticsMapper analyticsMapper;
    private BookIndexMapper bookIndexMapper;
    private AuthorBookCollaborationService collaborationService;
    private AuthorAnalyticsServiceImpl service;

    @BeforeEach
    void setUp() {
        analyticsMapper = mock(ReaderAnalyticsMapper.class);
        bookIndexMapper = mock(BookIndexMapper.class);
        collaborationService = mock(AuthorBookCollaborationService.class);
        service = new AuthorAnalyticsServiceImpl(analyticsMapper, bookIndexMapper, collaborationService);
    }

    @Test
    void completeEventIsAnonymizedNormalizedAndIdempotencyKeyIsPreserved() {
        BookIndex index = new BookIndex();
        index.setId(20L);
        index.setBookId(10L);
        when(bookIndexMapper.selectByPrimaryKey(20L)).thenReturn(Optional.of(index));

        ReaderAnalyticsEventInput input = event("COMPLETE");
        input.setProgressPercent(91);
        input.setDurationSeconds(42);
        service.recordReadEvent(input);

        ArgumentCaptor<ReaderAnalyticsEventRow> row = ArgumentCaptor.forClass(ReaderAnalyticsEventRow.class);
        verify(analyticsMapper).insertEvent(row.capture());
        assertThat(row.getValue().getClientEventId()).isEqualTo("pv_1234567890abcdef:COMPLETE:100");
        assertThat(row.getValue().getReaderKeyHash())
            .isEqualTo(ContentHashUtil.sha256Hex("ANALYTICS_V1:v1_1234567890abcdef1234567890abcdef"))
            .hasSize(64);
        assertThat(row.getValue().getEventType()).isEqualTo("COMPLETE");
        assertThat(row.getValue().getProgressPercent()).isEqualTo(100);
        assertThat(row.getValue().getDurationSeconds()).isEqualTo(42);
    }

    @Test
    void eventForChapterOutsideDeclaredBookIsRejectedBeforeInsert() {
        BookIndex index = new BookIndex();
        index.setId(20L);
        index.setBookId(99L);
        when(bookIndexMapper.selectByPrimaryKey(20L)).thenReturn(Optional.of(index));

        assertThatThrownBy(() -> service.recordReadEvent(event("START")))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("không thuộc truyện");
        verifyNoInteractions(analyticsMapper);
    }

    @Test
    void authorCannotReadAnalyticsForAnotherAuthorsBook() {
        when(collaborationService.requirePermission(7L, 10L, BookPermission.VIEW_ANALYTICS))
            .thenThrow(new com.java2nb.novel.core.exception.BusinessException(
                com.java2nb.novel.core.enums.ResponseStatus.AUTHOR_BOOK_ACCESS_FORBIDDEN));

        assertThatThrownBy(() -> service.getSummary(7L, 10L, null, null))
            .isInstanceOf(com.java2nb.novel.core.exception.BusinessException.class);
        verify(analyticsMapper, never()).selectSummary(anyLong(), anyLong(), any(), any());
    }

    @Test
    void defaultsToThirtyInclusiveDaysAndPaginatesChapters() {
        when(collaborationService.requirePermission(8L, 10L, BookPermission.VIEW_ANALYTICS))
            .thenReturn(access(8L, 10L, 7L));
        when(analyticsMapper.countChapters(10L)).thenReturn(41L);
        when(analyticsMapper.selectChapterAnalytics(anyLong(), anyLong(), any(), any(), anyInt(), anyInt()))
            .thenReturn(List.of(new AuthorChapterAnalyticsRow()));

        AuthorAnalyticsPage result = service.getChapterAnalytics(8L, 10L, null, null, 2, 20);

        ArgumentCaptor<Date> start = ArgumentCaptor.forClass(Date.class);
        ArgumentCaptor<Date> end = ArgumentCaptor.forClass(Date.class);
        verify(analyticsMapper).selectChapterAnalytics(eq(10L), eq(7L), start.capture(), end.capture(),
            eq(20), eq(20));
        assertThat(Duration.between(start.getValue().toInstant(), end.getValue().toInstant()).toDays())
            .isEqualTo(30);
        assertThat(result.getTotal()).isEqualTo(41L);
        assertThat(result.getPage()).isEqualTo(2);
    }

    @Test
    void dateRangeLongerThanOneYearIsRejected() {
        when(collaborationService.requirePermission(7L, 10L, BookPermission.VIEW_ANALYTICS))
            .thenReturn(access(7L, 10L, 7L));

        assertThatThrownBy(() -> service.getSummary(7L, 10L,
            LocalDate.of(2025, 1, 1), LocalDate.of(2026, 1, 2)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("366 ngày");
    }

    private AuthorBookAccess access(long actorId, long bookId, long ownerId) {
        AuthorBookAccess access = new AuthorBookAccess();
        access.setActorAuthorId(actorId);
        access.setBookId(bookId);
        access.setOwnerAuthorId(ownerId);
        access.setOwner(actorId == ownerId);
        access.setCanViewAnalytics(true);
        return access;
    }

    private ReaderAnalyticsEventInput event(String type) {
        ReaderAnalyticsEventInput input = new ReaderAnalyticsEventInput();
        input.setClientEventId("pv_1234567890abcdef:" + type + ("COMPLETE".equals(type) ? ":100" : ":0"));
        input.setVisitorId("v1_1234567890abcdef1234567890abcdef");
        input.setBookId(10L);
        input.setIndexId(20L);
        input.setEventType(type);
        input.setProgressPercent(0);
        input.setDurationSeconds(0);
        return input;
    }
}
