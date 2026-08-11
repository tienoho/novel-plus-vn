package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.observability.NovelBusinessMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.service.gamification.EventProcessResult;
import com.java2nb.novel.service.impl.GamificationEventFailureWriter;
import com.java2nb.novel.service.impl.GamificationEventProcessor;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GamificationEventDrainScheduleTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");
    private static final Date NOW_DATE = Date.from(NOW);

    private GamificationConfigProvider configProvider;
    private GamificationProgressMapper mapper;
    private GamificationEventProcessor processor;
    private GamificationEventFailureWriter failureWriter;
    private GamificationEventDrainSchedule schedule;

    @BeforeEach
    void setUp() {
        configProvider = mock(GamificationConfigProvider.class);
        when(configProvider.currentForWrite()).thenReturn(
            GamificationConfigSnapshot.bootstrapDisabled().toBuilder()
                .eventEnabled(true).questEnabled(true).build());
        mapper = mock(GamificationProgressMapper.class);
        processor = mock(GamificationEventProcessor.class);
        failureWriter = mock(GamificationEventFailureWriter.class);
        NovelBusinessMetrics metrics = new NovelBusinessMetrics(new SimpleMeterRegistry());
        schedule = new GamificationEventDrainSchedule(configProvider, mapper, processor, failureWriter,
            metrics, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void drainsPendingEventsWithConfiguredBatchAndRetryLimit() {
        when(mapper.selectPendingEventIds(200, 10)).thenReturn(List.of(51L));
        when(processor.process(51L, NOW_DATE, 10)).thenReturn(EventProcessResult.PROCESSED);

        schedule.drain();

        verify(processor).process(51L, NOW_DATE, 10);
        verify(failureWriter, never()).record(51L, NOW_DATE, null, 10);
    }

    @Test
    void recordsFailureWithoutStoppingTheRemainingBatch() {
        IllegalStateException failure = new IllegalStateException("quest write failed");
        when(mapper.selectPendingEventIds(200, 10)).thenReturn(List.of(51L, 52L));
        when(processor.process(51L, NOW_DATE, 10)).thenThrow(failure);
        when(processor.process(52L, NOW_DATE, 10)).thenReturn(EventProcessResult.SKIPPED);

        schedule.drain();

        verify(failureWriter).record(51L, NOW_DATE, failure, 10);
        verify(processor).process(52L, NOW_DATE, 10);
    }

    @Test
    void doesNothingWhenQuestProcessingIsDisabled() {
        when(configProvider.currentForWrite()).thenReturn(
            GamificationConfigSnapshot.bootstrapDisabled().toBuilder().eventEnabled(true).build());

        schedule.drain();

        verify(mapper, never()).selectPendingEventIds(200, 10);
    }
}
