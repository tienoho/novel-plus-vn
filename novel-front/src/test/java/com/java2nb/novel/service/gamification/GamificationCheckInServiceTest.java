package com.java2nb.novel.service.gamification;

import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.service.impl.GamificationEventFailureWriter;
import com.java2nb.novel.service.impl.GamificationEventProcessor;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class GamificationCheckInServiceTest {

    private static final long USER_ID = 11L;
    private static final long EVENT_ID = 41L;
    private static final Instant NOW = Instant.parse("2026-07-30T16:59:59Z");
    private static final LocalDate LOCAL_DATE = LocalDate.of(2026, 7, 30);
    private static final Date CHECKED_AT = Date.from(NOW);
    private static final String SOURCE_KEY = "CHECKIN:11:2026-07-30";

    private GamificationProgressService progressService;
    private GamificationProgressMapper mapper;
    private GamificationEventProcessor eventProcessor;
    private GamificationEventFailureWriter failureWriter;
    private GamificationConfigSnapshot config;
    private Clock clock;
    private GamificationCheckInService service;

    @BeforeEach
    void setUp() {
        progressService = mock(GamificationProgressService.class);
        mapper = mock(GamificationProgressMapper.class);
        eventProcessor = mock(GamificationEventProcessor.class);
        failureWriter = mock(GamificationEventFailureWriter.class);
        config = GamificationConfigSnapshot.bootstrapDisabled();
        clock = mock(Clock.class);
        when(clock.instant()).thenReturn(NOW, Instant.parse("2026-07-30T17:00:01Z"));
        service = new GamificationCheckInService(progressService, mapper, eventProcessor,
            failureWriter, clock);
    }

    @Test
    void processesEventClaimsDailyRewardAndUsesOneCapturedInstant() {
        CheckInResult checkIn = checkIn(false);
        GamificationEventRow event = event();
        QuestClaimResult reward = reward(false);
        when(progressService.checkIn(USER_ID, LOCAL_DATE, CHECKED_AT,
            java.time.ZoneId.of(config.getZoneId()), "v1", "v1",
            config.getRuntimeRevision())).thenReturn(checkIn);
        when(mapper.selectEventBySourceKey(SOURCE_KEY)).thenReturn(event);
        when(eventProcessor.process(EVENT_ID, CHECKED_AT, 10))
            .thenReturn(EventProcessResult.PROCESSED);
        when(progressService.claimQuest(claimCommand())).thenReturn(reward);

        CheckInOutcome outcome = service.checkIn(USER_ID, config);

        assertThat(outcome.checkIn()).isSameAs(checkIn);
        assertThat(outcome.reward()).isSameAs(reward);
        assertThat(outcome.nextCheckIn())
            .isEqualTo(Date.from(Instant.parse("2026-07-30T17:00:00Z")));
        verify(clock, times(1)).instant();
        verify(progressService).claimQuest(claimCommand());
        verifyNoInteractions(failureWriter);
    }

    @Test
    void replaysClaimWhenAnotherRequestAlreadyProcessedEvent() {
        when(progressService.checkIn(USER_ID, LOCAL_DATE, CHECKED_AT,
            java.time.ZoneId.of(config.getZoneId()), "v1", "v1",
            config.getRuntimeRevision())).thenReturn(checkIn(true));
        when(mapper.selectEventBySourceKey(SOURCE_KEY)).thenReturn(event());
        when(eventProcessor.process(EVENT_ID, CHECKED_AT, 10))
            .thenReturn(EventProcessResult.NOT_OWNER);
        when(progressService.claimQuest(claimCommand())).thenReturn(reward(true));

        CheckInOutcome outcome = service.checkIn(USER_ID, config);

        assertThat(outcome.checkIn().alreadyCheckedIn()).isTrue();
        assertThat(outcome.reward().alreadyClaimed()).isTrue();
        verify(progressService).claimQuest(claimCommand());
    }

    @Test
    void recordsProcessorFailureAndDoesNotClaimReward() {
        IllegalStateException failure = new IllegalStateException("processor failed");
        when(progressService.checkIn(USER_ID, LOCAL_DATE, CHECKED_AT,
            java.time.ZoneId.of(config.getZoneId()), "v1", "v1",
            config.getRuntimeRevision())).thenReturn(checkIn(false));
        when(mapper.selectEventBySourceKey(SOURCE_KEY)).thenReturn(event());
        when(eventProcessor.process(EVENT_ID, CHECKED_AT, 10)).thenThrow(failure);

        assertThatThrownBy(() -> service.checkIn(USER_ID, config)).isSameAs(failure);

        verify(failureWriter).record(EVENT_ID, CHECKED_AT, failure, 10);
        verify(progressService, never()).claimQuest(claimCommand());
    }

    @Test
    void failsClosedWhenEventIsMissing() {
        when(progressService.checkIn(USER_ID, LOCAL_DATE, CHECKED_AT,
            java.time.ZoneId.of(config.getZoneId()), "v1", "v1",
            config.getRuntimeRevision())).thenReturn(checkIn(false));
        when(mapper.selectEventBySourceKey(SOURCE_KEY)).thenReturn(null);

        assertThatThrownBy(() -> service.checkIn(USER_ID, config))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Không tìm thấy event điểm danh");

        verify(eventProcessor, never()).process(EVENT_ID, CHECKED_AT, 10);
        verify(progressService, never()).claimQuest(claimCommand());
    }

    @Test
    void failsClosedWhenEventMatchesNoActiveQuest() {
        when(progressService.checkIn(USER_ID, LOCAL_DATE, CHECKED_AT,
            java.time.ZoneId.of(config.getZoneId()), "v1", "v1",
            config.getRuntimeRevision())).thenReturn(checkIn(false));
        when(mapper.selectEventBySourceKey(SOURCE_KEY)).thenReturn(event());
        when(eventProcessor.process(EVENT_ID, CHECKED_AT, 10))
            .thenReturn(EventProcessResult.SKIPPED);

        assertThatThrownBy(() -> service.checkIn(USER_ID, config))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("không khớp nhiệm vụ đang hoạt động");

        verify(progressService, never()).claimQuest(claimCommand());
    }

    private CheckInResult checkIn(boolean alreadyCheckedIn) {
        GamificationProfileRow profile = profile();
        return new CheckInResult(profile, SOURCE_KEY, alreadyCheckedIn);
    }

    private GamificationEventRow event() {
        GamificationEventRow event = new GamificationEventRow();
        event.setId(EVENT_ID);
        event.setSourceKey(SOURCE_KEY);
        event.setStatus("PENDING");
        return event;
    }

    private QuestClaimResult reward(boolean alreadyClaimed) {
        QuestClaimRow claim = new QuestClaimRow();
        claim.setQuestCode("DAILY_CHECK_IN");
        claim.setPeriodKey(LOCAL_DATE.toString());
        claim.setExpAmount(10L);
        claim.setTicketAmount(1L);
        return new QuestClaimResult(claim,
            new GamificationProfileSnapshot(profile(), 500L), 4L, alreadyClaimed);
    }

    private QuestClaimCommand claimCommand() {
        return new QuestClaimCommand(USER_ID, "DAILY_CHECK_IN", LOCAL_DATE, CHECKED_AT,
            java.time.ZoneId.of(config.getZoneId()), 60, "v1", "v1",
            config.getRuntimeRevision());
    }

    private GamificationProfileRow profile() {
        GamificationProfileRow profile = new GamificationProfileRow();
        profile.setUserId(USER_ID);
        profile.setLevel(2);
        profile.setTotalExp(100L);
        profile.setRuleVersion("v1");
        profile.setCheckinStreak(3);
        profile.setLongestStreak(5);
        profile.setVersion(4L);
        return profile;
    }
}
