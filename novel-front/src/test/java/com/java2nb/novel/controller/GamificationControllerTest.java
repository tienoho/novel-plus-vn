package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.i18n.Messages;
import com.java2nb.novel.dto.gamification.RealmUpdateRequest;
import com.java2nb.novel.service.gamification.GamificationProfileRow;
import com.java2nb.novel.service.gamification.GamificationProfileSnapshot;
import com.java2nb.novel.service.gamification.CheckInOutcome;
import com.java2nb.novel.service.gamification.CheckInResult;
import com.java2nb.novel.service.gamification.GamificationCheckInService;
import com.java2nb.novel.service.gamification.GamificationProgressService;
import com.java2nb.novel.service.gamification.RealmUpdateResult;
import com.java2nb.novel.service.gamification.QuestProgressRow;
import com.java2nb.novel.service.gamification.QuestClaimRow;
import com.java2nb.novel.service.gamification.QuestClaimResult;
import com.java2nb.novel.service.gamification.TicketAccountRow;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class GamificationControllerTest {

    private GamificationProgressService service;
    private GamificationConfigProvider configProvider;
    private GamificationConfigSnapshot config;
    private UserDetails user;
    private Messages messages;
    private GamificationCheckInService checkInService;
    private GamificationController controller;

    @BeforeEach
    void setUp() {
        service = mock(GamificationProgressService.class);
        configProvider = mock(GamificationConfigProvider.class);
        config = GamificationConfigSnapshot.bootstrapDisabled().toBuilder()
            .realmEnabled(true).eventEnabled(true).questEnabled(true).build();
        when(configProvider.current()).thenReturn(config);
        when(configProvider.currentForWrite()).thenReturn(config);
        messages = mock(Messages.class);
        checkInService = mock(GamificationCheckInService.class);
        user = mock(UserDetails.class);
        when(user.getId()).thenReturn(11L);
        controller = new GamificationController(service, configProvider, messages,
            checkInService,
            Clock.fixed(Instant.parse("2026-07-30T03:00:00Z"), ZoneOffset.UTC)) {
            @Override
            protected UserDetails getUserDetails(HttpServletRequest request) {
                return user;
            }
        };
    }

    @Test
    void profileUsesAuthenticatedIdentityAndReturnsNextThreshold() {
        GamificationProfileSnapshot snapshot = new GamificationProfileSnapshot(profile("NHAP_MON", 4L), 500L);
        when(service.getProfileSnapshot(11L, "v1")).thenReturn(snapshot);

        var response = controller.getProfile(new MockHttpServletRequest());

        assertThat(response.getData().nextLevelExp()).isEqualTo(500L);
        verify(service).getProfileSnapshot(11L, "v1");
    }

    @Test
    void realmUpdateUsesServerTimeZonePolicyAndCurrentUser() {
        GamificationProfileRow updated = profile("TIEN_PHONG", 5L);
        RealmUpdateResult result = new RealmUpdateResult(updated,
            Date.from(Instant.parse("2026-07-31T03:00:00Z")));
        GamificationProfileSnapshot snapshot = new GamificationProfileSnapshot(updated, 1_000L);
        Date changedAt = Date.from(Instant.parse("2026-07-30T03:00:00Z"));
        when(service.updateRealm(11L, "TIEN_PHONG", 4L, changedAt,
            java.time.ZoneId.of(config.getZoneId()), 24, "v1")).thenReturn(result);
        when(service.getProfileSnapshot(11L, "v1")).thenReturn(snapshot);

        var response = controller.updateRealm(new RealmUpdateRequest("TIEN_PHONG", 4L),
            new MockHttpServletRequest());

        assertThat(response.getData().profile().realmCode()).isEqualTo("TIEN_PHONG");
        assertThat(response.getData().profile().nextLevelExp()).isEqualTo(1_000L);
        verify(service).updateRealm(11L, "TIEN_PHONG", 4L, changedAt,
            java.time.ZoneId.of(config.getZoneId()), 24, "v1");
    }

    @Test
    void questListUsesServerDateAndLocalizedName() {
        QuestProgressRow row = new QuestProgressRow();
        row.setQuestCode("DAILY_READING");
        row.setNameKey("quest.dailyReading");
        row.setPeriodType("DAILY");
        row.setPeriodKey("2026-07-30");
        row.setCurrentCount(30);
        row.setTargetCount(30);
        row.setCompletedAt(new Date());
        Date observedAt = Date.from(Instant.parse("2026-07-30T03:00:00Z"));
        when(service.listQuests(11L, java.time.LocalDate.of(2026, 7, 30), observedAt, "v1"))
            .thenReturn(List.of(row));
        when(messages.get("quest.dailyReading")).thenReturn("Đọc truyện đủ 30 phút");

        var response = controller.getQuests(null, new MockHttpServletRequest());

        assertThat(response.getData()).hasSize(1);
        assertThat(response.getData().get(0).name()).isEqualTo("Đọc truyện đủ 30 phút");
        assertThat(response.getData().get(0).completed()).isTrue();
    }

    @Test
    void questClaimUsesAuthenticatedUserAndServerTime() {
        QuestClaimRow claim = new QuestClaimRow();
        claim.setQuestCode("DAILY_READING");
        claim.setPeriodKey("2026-07-30");
        claim.setExpAmount(20L);
        claim.setTicketAmount(1L);
        GamificationProfileRow profile = profile("NHAP_MON", 5L);
        QuestClaimResult result = new QuestClaimResult(claim,
            new GamificationProfileSnapshot(profile, 500L), 4L, false);
        var expected = new com.java2nb.novel.service.gamification.QuestClaimCommand(11L,
            "DAILY_READING", java.time.LocalDate.of(2026, 7, 30),
            Date.from(Instant.parse("2026-07-30T03:00:00Z")),
            java.time.ZoneId.of(config.getZoneId()), 60, "v1", "v1",
            config.getRuntimeRevision());
        when(service.claimQuest(expected)).thenReturn(result);

        var response = controller.claimQuest("DAILY_READING", new MockHttpServletRequest());

        assertThat(response.getData().expGained()).isEqualTo(20L);
        assertThat(response.getData().ticketBalance()).isEqualTo(4L);
        verify(service).claimQuest(expected);
    }

    @Test
    void questClaimCapturesOneInstantForTimestampAndLocalDate() {
        Clock movingClock = mock(Clock.class);
        Instant beforeMidnight = Instant.parse("2026-07-30T16:59:59Z");
        when(movingClock.instant()).thenReturn(beforeMidnight,
            Instant.parse("2026-07-30T17:00:01Z"));
        GamificationController movingController = new GamificationController(
            service, configProvider, messages, checkInService, movingClock) {
            @Override
            protected UserDetails getUserDetails(HttpServletRequest request) {
                return user;
            }
        };
        QuestClaimRow claim = new QuestClaimRow();
        claim.setQuestCode("DAILY_READING");
        claim.setPeriodKey("2026-07-30");
        claim.setExpAmount(20L);
        claim.setTicketAmount(0L);
        QuestClaimResult result = new QuestClaimResult(claim,
            new GamificationProfileSnapshot(profile("NHAP_MON", 5L), 500L), 0L, false);
        var expected = new com.java2nb.novel.service.gamification.QuestClaimCommand(11L,
            "DAILY_READING", java.time.LocalDate.of(2026, 7, 30), Date.from(beforeMidnight),
            java.time.ZoneId.of(config.getZoneId()), 60, "v1", "v1",
            config.getRuntimeRevision());
        when(service.claimQuest(expected)).thenReturn(result);

        movingController.claimQuest("DAILY_READING", new MockHttpServletRequest());

        verify(movingClock, times(1)).instant();
        verify(service).claimQuest(expected);
    }

    @Test
    void checkInUsesAuthenticatedUserAndReturnsAutoClaimedReward() {
        GamificationProfileRow checkedInProfile = profile("NHAP_MON", 5L);
        checkedInProfile.setCheckinStreak(3);
        checkedInProfile.setLongestStreak(6);
        QuestClaimRow claim = new QuestClaimRow();
        claim.setQuestCode("DAILY_CHECK_IN");
        claim.setPeriodKey("2026-07-30");
        claim.setExpAmount(10L);
        claim.setTicketAmount(1L);
        GamificationProfileRow rewardedProfile = profile("NHAP_MON", 6L);
        rewardedProfile.setTotalExp(260L);
        QuestClaimResult reward = new QuestClaimResult(claim,
            new GamificationProfileSnapshot(rewardedProfile, 500L), 5L, false);
        Date nextCheckIn = Date.from(Instant.parse("2026-07-30T17:00:00Z"));
        CheckInOutcome outcome = new CheckInOutcome(
            new CheckInResult(checkedInProfile, "CHECKIN:11:2026-07-30", false),
            reward, nextCheckIn);
        when(checkInService.checkIn(11L, config)).thenReturn(outcome);

        var response = controller.checkIn(new MockHttpServletRequest());

        assertThat(response.getData().streak()).isEqualTo(3);
        assertThat(response.getData().longestStreak()).isEqualTo(6);
        assertThat(response.getData().expGained()).isEqualTo(10L);
        assertThat(response.getData().ticketGained()).isEqualTo(1L);
        assertThat(response.getData().totalExp()).isEqualTo(260L);
        assertThat(response.getData().ticketBalance()).isEqualTo(5L);
        assertThat(response.getData().nextCheckIn()).isEqualTo(nextCheckIn);
        assertThat(response.getData().alreadyCheckedIn()).isFalse();
        verify(checkInService).checkIn(11L, config);
    }

    private GamificationProfileRow profile(String realm, long version) {
        GamificationProfileRow row = new GamificationProfileRow();
        row.setUserId(11L);
        row.setLevel(3);
        row.setTotalExp(250L);
        row.setRuleVersion("v1");
        row.setRealmCode(realm);
        row.setCheckinStreak(2);
        row.setLongestStreak(4);
        row.setVersion(version);
        return row;
    }
}
