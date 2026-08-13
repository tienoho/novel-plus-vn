package com.java2nb.novel.service.gamification;

import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.service.impl.GamificationEventRecorderImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GamificationEventRecorderImplTest {

    private GamificationProgressMapper mapper;
    private GamificationEventRecorderImpl recorder;

    @BeforeEach
    void setUp() {
        mapper = mock(GamificationProgressMapper.class);
        recorder = new GamificationEventRecorderImpl(mapper);
    }

    @Test
    void returnsPostedWhenUniqueEventIsInserted() {
        GamificationEventInput event = event("a".repeat(64));
        when(mapper.insertEventIgnore(event)).thenReturn(1);

        assertThat(recorder.ingest(event)).isEqualTo(GamificationEventPostResult.POSTED);
    }

    @Test
    void returnsAlreadyPostedOnlyWhenExistingContentMatches() {
        GamificationEventInput event = event("a".repeat(64));
        when(mapper.insertEventIgnore(event)).thenReturn(0);
        when(mapper.selectEventBySourceKey(event.sourceKey())).thenReturn(row(event));

        assertThat(recorder.ingest(event)).isEqualTo(GamificationEventPostResult.ALREADY_POSTED);
    }

    @Test
    void rejectsReuseOfSourceKeyForDifferentPayload() {
        GamificationEventInput event = event("a".repeat(64));
        GamificationEventRow existing = row(event);
        existing.setPayloadHash("b".repeat(64));
        when(mapper.insertEventIgnore(event)).thenReturn(0);
        when(mapper.selectEventBySourceKey(event.sourceKey())).thenReturn(existing);

        assertThatThrownBy(() -> recorder.ingest(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("nội dung khác");
    }

    @Test
    void failsWhenIgnoredInsertCannotBeReadBack() {
        GamificationEventInput event = event("a".repeat(64));
        when(mapper.insertEventIgnore(event)).thenReturn(0);

        assertThatThrownBy(() -> recorder.ingest(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Không đọc được");
    }

    private GamificationEventInput event(String hash) {
        return new GamificationEventInput("CHAPTER_PURCHASED", "GAMIFY:CHAPTER_PURCHASE:11:22",
            11L, 33L, new Date(1_000), LocalDate.of(2026, 7, 30), hash, null, "v1", 7L);
    }

    private GamificationEventRow row(GamificationEventInput event) {
        GamificationEventRow row = new GamificationEventRow();
        row.setEventType(event.eventType());
        row.setSourceKey(event.sourceKey());
        row.setUserId(event.userId());
        row.setBookId(event.bookId());
        row.setLocalDate(event.localDate());
        row.setPayloadHash(event.payloadHash());
        row.setPolicyVersion(event.policyVersion());
        row.setRuntimeConfigRevision(event.runtimeConfigRevision());
        return row;
    }
}
