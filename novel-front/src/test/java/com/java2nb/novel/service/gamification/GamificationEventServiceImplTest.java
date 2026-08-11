package com.java2nb.novel.service.gamification;

import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GamificationEventServiceImplTest {

    @Test
    void disabledEventCollectionDoesNotTouchRecorder() {
        GamificationConfigProvider provider = provider(GamificationConfigSnapshot.bootstrapDisabled());
        GamificationEventRecorder recorder = mock(GamificationEventRecorder.class);

        new GamificationEventServiceImpl(provider, recorder)
            .ingest("TOP_UP_SETTLED", "TOPUP:1", 11L, null, new Date(), null);

        verify(recorder, never()).ingest(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void enabledCollectionUsesConfiguredVietnamDateAndStableHash() {
        GamificationConfigProvider provider = provider(
            GamificationConfigSnapshot.bootstrapDisabled().toBuilder().eventEnabled(true).build());
        GamificationEventRecorder recorder = mock(GamificationEventRecorder.class);
        Date occurredAt = Date.from(java.time.Instant.parse("2026-07-29T17:30:00Z"));

        new GamificationEventServiceImpl(provider, recorder)
            .ingest("CHAPTER_PURCHASED", "GAMIFY:CHAPTER_PURCHASE:11:22",
                11L, 33L, occurredAt, null);

        ArgumentCaptor<GamificationEventInput> event = ArgumentCaptor.forClass(GamificationEventInput.class);
        verify(recorder).ingest(event.capture());
        assertThat(event.getValue().localDate()).isEqualTo(LocalDate.of(2026, 7, 30));
        assertThat(event.getValue().payloadHash()).matches("[0-9a-f]{64}");
        assertThat(event.getValue().payloadJson()).isNull();
    }

    private GamificationConfigProvider provider(GamificationConfigSnapshot snapshot) {
        GamificationConfigProvider provider = mock(GamificationConfigProvider.class);
        when(provider.currentForWrite()).thenReturn(snapshot);
        return provider;
    }
}
