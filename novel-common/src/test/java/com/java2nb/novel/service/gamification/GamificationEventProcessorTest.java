package com.java2nb.novel.service.gamification;

import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.service.impl.GamificationEventProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GamificationEventProcessorTest {

    private static final long EVENT_ID = 41L;
    private static final Date PROCESSED_AT = Date.from(Instant.parse("2026-07-30T03:00:00Z"));

    private GamificationProgressMapper mapper;
    private GamificationProgressService progressService;
    private GamificationEventProcessor processor;

    @BeforeEach
    void setUp() {
        mapper = mock(GamificationProgressMapper.class);
        progressService = mock(GamificationProgressService.class);
        processor = new GamificationEventProcessor(mapper, progressService);
    }

    @Test
    void completesClaimedEventAsProcessedWhenAQuestMatches() {
        GamificationEventRow event = pendingEvent();
        when(mapper.selectEventById(EVENT_ID)).thenReturn(event);
        when(mapper.claimEvent(EVENT_ID, 3L, PROCESSED_AT, 10)).thenReturn(1);
        when(progressService.applyEvent(event)).thenReturn(1);
        when(mapper.completeEvent(EVENT_ID, 4L, "PROCESSED", PROCESSED_AT)).thenReturn(1);

        assertThat(processor.process(EVENT_ID, PROCESSED_AT, 10))
            .isEqualTo(EventProcessResult.PROCESSED);
    }

    @Test
    void completesClaimedEventAsSkippedWhenNoQuestMatches() {
        GamificationEventRow event = pendingEvent();
        when(mapper.selectEventById(EVENT_ID)).thenReturn(event);
        when(mapper.claimEvent(EVENT_ID, 3L, PROCESSED_AT, 10)).thenReturn(1);
        when(progressService.applyEvent(event)).thenReturn(0);
        when(mapper.completeEvent(EVENT_ID, 4L, "SKIPPED", PROCESSED_AT)).thenReturn(1);

        assertThat(processor.process(EVENT_ID, PROCESSED_AT, 10))
            .isEqualTo(EventProcessResult.SKIPPED);
    }

    @Test
    void returnsNotOwnerWithoutApplyingEventWhenClaimFails() {
        GamificationEventRow event = pendingEvent();
        when(mapper.selectEventById(EVENT_ID)).thenReturn(event);
        when(mapper.claimEvent(EVENT_ID, 3L, PROCESSED_AT, 10)).thenReturn(0);

        assertThat(processor.process(EVENT_ID, PROCESSED_AT, 10))
            .isEqualTo(EventProcessResult.NOT_OWNER);

        verifyNoInteractions(progressService);
        verify(mapper, never()).completeEvent(EVENT_ID, 4L, "PROCESSED", PROCESSED_AT);
    }

    @Test
    void failsTransactionWhenClaimCanNoLongerBeCompleted() {
        GamificationEventRow event = pendingEvent();
        when(mapper.selectEventById(EVENT_ID)).thenReturn(event);
        when(mapper.claimEvent(EVENT_ID, 3L, PROCESSED_AT, 10)).thenReturn(1);
        when(progressService.applyEvent(event)).thenReturn(1);
        when(mapper.completeEvent(EVENT_ID, 4L, "PROCESSED", PROCESSED_AT)).thenReturn(0);

        assertThatThrownBy(() -> processor.process(EVENT_ID, PROCESSED_AT, 10))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Mất quyền sở hữu event");
    }

    private GamificationEventRow pendingEvent() {
        GamificationEventRow event = new GamificationEventRow();
        event.setId(EVENT_ID);
        event.setStatus("PENDING");
        event.setVersion(3L);
        return event;
    }
}
