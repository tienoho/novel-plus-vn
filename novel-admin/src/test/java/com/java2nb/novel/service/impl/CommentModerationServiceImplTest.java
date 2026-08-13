package com.java2nb.novel.service.impl;

import com.java2nb.novel.entity.BookComment;
import com.java2nb.novel.mapper.BookCommentMapper;
import com.java2nb.novel.service.gamification.GamificationEventInput;
import com.java2nb.novel.service.gamification.GamificationEventRecorder;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CommentModerationServiceImplTest {

    @Test
    void productionConstructorIsExplicitlyAutowiredBecauseTestConstructorAlsoExists() throws Exception {
        assertThat(CommentModerationServiceImpl.class.getConstructor(
            BookCommentMapper.class, GamificationEventRecorder.class,
            GamificationConfigProvider.class).getAnnotation(Autowired.class)).isNotNull();
    }

    @Test
    void emitsOnceOnlyWhenCommentTransitionsToApproved() {
        BookCommentMapper mapper = mock(BookCommentMapper.class);
        GamificationEventRecorder recorder = mock(GamificationEventRecorder.class);
        GamificationConfigProvider configProvider = enabledProvider();
        BookComment comment = new BookComment();
        comment.setId(7L);
        comment.setBookId(8L);
        comment.setCreateUserId(9L);
        when(mapper.selectByPrimaryKey(7L)).thenReturn(Optional.of(comment));
        when(mapper.update(any(org.mybatis.dynamic.sql.update.UpdateDSLCompleter.class))).thenReturn(1);
        CommentModerationServiceImpl service = new CommentModerationServiceImpl(mapper, recorder,
            configProvider,
            Clock.fixed(Instant.parse("2026-07-29T18:00:00Z"), ZoneOffset.UTC));

        assertThat(service.batchAudit(new Long[]{7L, 7L}, (byte) 1)).isEqualTo(1);

        ArgumentCaptor<GamificationEventInput> event = ArgumentCaptor.forClass(GamificationEventInput.class);
        verify(recorder).ingest(event.capture());
        assertThat(event.getValue().sourceKey()).isEqualTo("GAMIFY:COMMENT_APPROVED:7");
        assertThat(event.getValue().userId()).isEqualTo(9L);
        assertThat(event.getValue().bookId()).isEqualTo(8L);
    }

    @Test
    void retryOrRejectionDoesNotEmitEvent() {
        BookCommentMapper mapper = mock(BookCommentMapper.class);
        GamificationEventRecorder recorder = mock(GamificationEventRecorder.class);
        GamificationConfigProvider configProvider = enabledProvider();
        BookComment comment = new BookComment();
        when(mapper.selectByPrimaryKey(7L)).thenReturn(Optional.of(comment));
        when(mapper.update(any(org.mybatis.dynamic.sql.update.UpdateDSLCompleter.class))).thenReturn(0);
        CommentModerationServiceImpl service = new CommentModerationServiceImpl(mapper, recorder,
            configProvider);

        assertThat(service.batchAudit(new Long[]{7L}, (byte) 1)).isZero();
        assertThat(service.batchAudit(new Long[]{7L}, (byte) 2)).isZero();
        verify(recorder, never()).ingest(any());
    }

    private GamificationConfigProvider enabledProvider() {
        GamificationConfigProvider provider = mock(GamificationConfigProvider.class);
        when(provider.currentForWrite()).thenReturn(
            GamificationConfigSnapshot.bootstrapDisabled().toBuilder().eventEnabled(true).build());
        return provider;
    }
}
