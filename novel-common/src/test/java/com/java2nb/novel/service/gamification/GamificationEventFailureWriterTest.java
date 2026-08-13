package com.java2nb.novel.service.gamification;

import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.service.impl.GamificationEventFailureWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GamificationEventFailureWriterTest {

    private static final long EVENT_ID = 42L;
    private static final Date FAILED_AT = Date.from(Instant.parse("2026-07-30T03:00:00Z"));

    private GamificationProgressMapper mapper;
    private GamificationEventFailureWriter writer;

    @BeforeEach
    void setUp() {
        mapper = mock(GamificationProgressMapper.class);
        writer = new GamificationEventFailureWriter(mapper);
    }

    @Test
    void truncatesPersistedFailureMessageToSchemaLimit() {
        GamificationEventRow event = new GamificationEventRow();
        event.setStatus("PENDING");
        event.setVersion(7L);
        when(mapper.selectEventById(EVENT_ID)).thenReturn(event);
        String longMessage = "x".repeat(501);

        writer.record(EVENT_ID, FAILED_AT, new IllegalStateException(longMessage), 10);

        verify(mapper).recordEventFailure(EVENT_ID, 7L, FAILED_AT, "x".repeat(500), 10);
    }

    @Test
    void doesNotRewriteAnEventThatIsNoLongerPending() {
        GamificationEventRow event = new GamificationEventRow();
        event.setStatus("PROCESSED");
        event.setVersion(8L);
        when(mapper.selectEventById(EVENT_ID)).thenReturn(event);

        writer.record(EVENT_ID, FAILED_AT, new IllegalStateException("late failure"), 10);

        verify(mapper, never()).recordEventFailure(EVENT_ID, 8L, FAILED_AT, "late failure", 10);
    }
}
