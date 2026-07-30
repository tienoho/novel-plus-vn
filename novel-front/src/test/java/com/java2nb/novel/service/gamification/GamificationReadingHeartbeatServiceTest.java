package com.java2nb.novel.service.gamification;

import com.java2nb.novel.core.config.GamificationProperties;
import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.service.impl.GamificationEventFailureWriter;
import com.java2nb.novel.service.impl.GamificationEventProcessor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GamificationReadingHeartbeatServiceTest {

    private static final Instant NOW = Instant.parse("2026-07-30T03:00:00Z");
    private static final Date HEARTBEAT_AT = Date.from(NOW);
    private static final String SOURCE_KEY =
        "READ:11:0123456789abcdef0123456789abcdef:2026-07-30:1";

    private ReadingHeartbeatWriter writer;
    private GamificationProgressMapper mapper;
    private GamificationEventProcessor processor;
    private GamificationEventFailureWriter failureWriter;
    private GamificationProperties properties;
    private Clock clock;
    private GamificationReadingHeartbeatService service;

    @BeforeEach
    void setUp() {
        writer = mock(ReadingHeartbeatWriter.class);
        mapper = mock(GamificationProgressMapper.class);
        processor = mock(GamificationEventProcessor.class);
        failureWriter = mock(GamificationEventFailureWriter.class);
        properties = new GamificationProperties();
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, NOW.plusSeconds(1));
        service = new GamificationReadingHeartbeatService(
            writer, mapper, processor, failureWriter, properties, clock);
    }

    @Test
    void capturesOneInstantWritesHeartbeatAndProcessesEveryMinuteEvent() {
        ReadingHeartbeatInput input = input();
        ReadingHeartbeatCommand command = command(input);
        ReadingHeartbeatResult written = new ReadingHeartbeatResult(input.sessionId(),
            60, 1, false, 2, false, List.of(SOURCE_KEY));
        GamificationEventRow event = event(51L);
        when(writer.record(command)).thenReturn(written);
        when(mapper.selectEventBySourceKey(SOURCE_KEY)).thenReturn(event);
        when(processor.process(51L, HEARTBEAT_AT, 10)).thenReturn(EventProcessResult.PROCESSED);

        ReadingHeartbeatResult result = service.record(11L, input);

        assertThat(result).isSameAs(written);
        verify(clock, times(1)).instant();
        verify(writer).record(command);
        verify(processor).process(51L, HEARTBEAT_AT, 10);
        verify(failureWriter, never()).record(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void recordsProcessorFailureAndFailsRequest() {
        ReadingHeartbeatInput input = input();
        when(writer.record(command(input))).thenReturn(new ReadingHeartbeatResult(input.sessionId(),
            60, 1, false, 2, false, List.of(SOURCE_KEY)));
        when(mapper.selectEventBySourceKey(SOURCE_KEY)).thenReturn(event(51L));
        IllegalStateException failure = new IllegalStateException("quest failed");
        when(processor.process(51L, HEARTBEAT_AT, 10)).thenThrow(failure);

        assertThatThrownBy(() -> service.record(11L, input)).isSameAs(failure);

        verify(failureWriter).record(51L, HEARTBEAT_AT, failure, 10);
    }

    @Test
    void failsClosedWhenMinuteEventIsMissing() {
        ReadingHeartbeatInput input = input();
        when(writer.record(command(input))).thenReturn(new ReadingHeartbeatResult(input.sessionId(),
            60, 1, false, 2, false, List.of(SOURCE_KEY)));
        when(mapper.selectEventBySourceKey(SOURCE_KEY)).thenReturn(null);

        assertThatThrownBy(() -> service.record(11L, input))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("event phút đọc");

        verify(processor, never()).process(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.any(),
            org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void failsClosedWhenNoActiveReadingQuestMatches() {
        ReadingHeartbeatInput input = input();
        when(writer.record(command(input))).thenReturn(new ReadingHeartbeatResult(input.sessionId(),
            60, 1, false, 2, false, List.of(SOURCE_KEY)));
        when(mapper.selectEventBySourceKey(SOURCE_KEY)).thenReturn(event(51L));
        when(processor.process(51L, HEARTBEAT_AT, 10)).thenReturn(EventProcessResult.SKIPPED);

        assertThatThrownBy(() -> service.record(11L, input))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("nhiệm vụ đọc");
    }

    private ReadingHeartbeatInput input() {
        return new ReadingHeartbeatInput("0123456789abcdef0123456789abcdef",
            21L, 31L, 1, 60);
    }

    private ReadingHeartbeatCommand command(ReadingHeartbeatInput input) {
        return new ReadingHeartbeatCommand(11L, input.sessionId(), input.bookId(),
            input.bookIndexId(), input.sequence(), input.activeSeconds(), HEARTBEAT_AT,
            LocalDate.of(2026, 7, 30), properties.resolveZoneId(), 60, 180, "v1");
    }

    private GamificationEventRow event(long id) {
        GamificationEventRow row = new GamificationEventRow();
        row.setId(id);
        row.setSourceKey(SOURCE_KEY);
        row.setStatus("PENDING");
        return row;
    }
}
