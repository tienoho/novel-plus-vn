package com.java2nb.novel.service.gamification;

import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.mapper.MonthlyTicketMapper;
import com.java2nb.novel.service.impl.GamificationProgressServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

class GamificationProgressServiceImplTest {

    private GamificationProgressMapper mapper;
    private MonthlyTicketService monthlyTicketService;
    private MonthlyTicketMapper monthlyTicketMapper;
    private GamificationEventRecorder eventRecorder;
    private GamificationProgressServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(GamificationProgressMapper.class);
        monthlyTicketService = mock(MonthlyTicketService.class);
        monthlyTicketMapper = mock(MonthlyTicketMapper.class);
        eventRecorder = mock(GamificationEventRecorder.class);
        service = new GamificationProgressServiceImpl(
            mapper, monthlyTicketService, monthlyTicketMapper, eventRecorder);
    }

    @Test
    void lazilyCreatesProfileWithRequestedRuleVersion() {
        GamificationProfileRow profile = profile(null, 0L, null);
        when(mapper.selectProfile(11L)).thenReturn(profile);

        assertThat(service.getProfile(11L, "v1")).isSameAs(profile);

        verify(mapper).insertProfileIgnore(11L, "v1");
    }

    @Test
    void snapshotReturnsNextThresholdFromSameRuleVersion() {
        GamificationProfileRow profile = profile(null, 0L, null);
        LevelRuleRow next = new LevelRuleRow();
        next.setMinExp(500L);
        when(mapper.selectProfile(11L)).thenReturn(profile);
        when(mapper.selectNextLevelRule("v1", 250L)).thenReturn(next);

        GamificationProfileSnapshot snapshot = service.getProfileSnapshot(11L, "v1");

        assertThat(snapshot.profile()).isSameAs(profile);
        assertThat(snapshot.nextLevelExp()).isEqualTo(500L);
    }

    @Test
    void changesEligibleRealmWithVersionAndAudit() {
        Date changedAt = Date.from(Instant.parse("2026-07-30T03:00:00Z"));
        GamificationProfileRow locked = profile("NHAP_MON", 4L,
            Date.from(Instant.parse("2026-07-28T03:00:00Z")));
        RealmCatalogRow realm = new RealmCatalogRow();
        realm.setRealmCode("TIEN_PHONG");
        realm.setMinLevel(3);
        realm.setActive(true);
        GamificationProfileRow updated = profile("TIEN_PHONG", 5L, changedAt);
        when(mapper.lockProfile(11L)).thenReturn(locked);
        when(mapper.selectRealm("TIEN_PHONG")).thenReturn(realm);
        when(mapper.updateRealm(11L, "TIEN_PHONG", changedAt, 4L)).thenReturn(1);
        when(mapper.insertProfileAudit(11L, "REALM", "NHAP_MON", "TIEN_PHONG",
            "USER", 11L, "Người dùng đổi cảnh giới")).thenReturn(1);
        when(mapper.selectProfile(11L)).thenReturn(updated);

        RealmUpdateResult result = service.updateRealm(11L, "TIEN_PHONG", 4L, changedAt,
            ZoneId.of("Asia/Ho_Chi_Minh"), 24, "v1");

        assertThat(result.profile()).isSameAs(updated);
        assertThat(result.cooldownUntil()).isEqualTo(Date.from(changedAt.toInstant().plusSeconds(86_400)));
    }

    @Test
    void rejectsStaleVersionBeforeChangingRealm() {
        when(mapper.lockProfile(11L)).thenReturn(profile("NHAP_MON", 5L, null));

        assertThatThrownBy(() -> service.updateRealm(11L, "TIEN_PHONG", 4L, new Date(),
            ZoneId.of("Asia/Ho_Chi_Minh"), 24, "v1"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void enforcesBothSameDayAndElapsedHourCooldown() {
        GamificationProfileRow profile = profile("NHAP_MON", 4L,
            Date.from(Instant.parse("2026-07-29T16:00:00Z")));
        RealmCatalogRow realm = new RealmCatalogRow();
        realm.setRealmCode("TIEN_PHONG");
        realm.setMinLevel(1);
        realm.setActive(true);
        when(mapper.lockProfile(11L)).thenReturn(profile);
        when(mapper.selectRealm("TIEN_PHONG")).thenReturn(realm);

        assertThatThrownBy(() -> service.updateRealm(11L, "TIEN_PHONG", 4L,
            Date.from(Instant.parse("2026-07-29T18:00:00Z")),
            ZoneId.of("Asia/Ho_Chi_Minh"), 24, "v1"))
            .isInstanceOf(BusinessException.class);
    }

    @Test
    void appliesDailyWeeklyAndOneTimeQuestPeriodsFromEventLocalDate() {
        Date occurredAt = Date.from(Instant.parse("2027-01-01T03:00:00Z"));
        GamificationEventRow event = event("CHAPTER_PURCHASED", LocalDate.of(2027, 1, 1), occurredAt);
        when(mapper.selectActiveQuestsByEventType("CHAPTER_PURCHASED")).thenReturn(List.of(
            quest("DAILY_PAID_CHAPTER", "DAILY", 1),
            quest("WEEKLY_PAID_CHAPTER", "WEEKLY", 2),
            quest("FIRST_PAID_CHAPTER", "ONE_TIME", 1)
        ));

        assertThat(service.applyEvent(event)).isEqualTo(3);

        verify(mapper).insertQuestProgressIgnore(11L, "DAILY_PAID_CHAPTER", "2027-01-01", 1);
        verify(mapper).incrementQuestProgress(11L, "DAILY_PAID_CHAPTER", "2027-01-01", occurredAt);
        verify(mapper).insertQuestProgressIgnore(11L, "WEEKLY_PAID_CHAPTER", "2026-W53", 2);
        verify(mapper).incrementQuestProgress(11L, "WEEKLY_PAID_CHAPTER", "2026-W53", occurredAt);
        verify(mapper).insertQuestProgressIgnore(11L, "FIRST_PAID_CHAPTER", "ALL", 1);
        verify(mapper).incrementQuestProgress(11L, "FIRST_PAID_CHAPTER", "ALL", occurredAt);
    }

    @Test
    void rejectsUnsupportedQuestPeriodBeforeWritingProgress() {
        GamificationEventRow event = event("CHAPTER_PURCHASED", LocalDate.of(2026, 7, 30), new Date());
        when(mapper.selectActiveQuestsByEventType("CHAPTER_PURCHASED"))
            .thenReturn(List.of(quest("BROKEN_QUEST", "MONTHLY", 1)));

        assertThatThrownBy(() -> service.applyEvent(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Chu kỳ nhiệm vụ không được hỗ trợ");
    }

    @Test
    void firstCheckInStartsStreakAndRecordsSourceEvent() {
        Date checkedAt = Date.from(Instant.parse("2026-07-30T03:00:00Z"));
        LocalDate localDate = LocalDate.of(2026, 7, 30);
        GamificationProfileRow profile = profile(null, 4L, null);
        profile.setCheckinStreak(0);
        profile.setLongestStreak(0);
        when(mapper.lockProfile(11L)).thenReturn(profile);
        when(mapper.updateCheckIn(11L, 4L, localDate, 1, 1)).thenReturn(1);

        CheckInResult result = service.checkIn(11L, localDate, checkedAt,
            ZoneId.of("Asia/Ho_Chi_Minh"), "v1", "v1");

        assertThat(result.alreadyCheckedIn()).isFalse();
        assertThat(result.sourceKey()).isEqualTo("CHECKIN:11:2026-07-30");
        assertThat(result.profile().getCheckinStreak()).isEqualTo(1);
        assertThat(result.profile().getLongestStreak()).isEqualTo(1);
        assertThat(result.profile().getLastCheckinDate()).isEqualTo(localDate);
        assertThat(result.profile().getVersion()).isEqualTo(5L);
        ArgumentCaptor<GamificationEventInput> event =
            ArgumentCaptor.forClass(GamificationEventInput.class);
        verify(eventRecorder).ingest(event.capture());
        assertThat(event.getValue().eventType()).isEqualTo("CHECK_IN_COMPLETED");
        assertThat(event.getValue().sourceKey()).isEqualTo("CHECKIN:11:2026-07-30");
        assertThat(event.getValue().localDate()).isEqualTo(localDate);
        assertThat(event.getValue().occurredAt()).isEqualTo(checkedAt);
        assertThat(event.getValue().payloadJson()).isEqualTo("{\"streak\":1}");
    }

    @Test
    void consecutiveCheckInIncrementsCurrentAndLongestStreak() {
        Date checkedAt = Date.from(Instant.parse("2026-07-30T03:00:00Z"));
        LocalDate localDate = LocalDate.of(2026, 7, 30);
        GamificationProfileRow profile = profile(null, 4L, null);
        profile.setLastCheckinDate(localDate.minusDays(1));
        profile.setCheckinStreak(4);
        profile.setLongestStreak(4);
        when(mapper.lockProfile(11L)).thenReturn(profile);
        when(mapper.updateCheckIn(11L, 4L, localDate, 5, 5)).thenReturn(1);

        CheckInResult result = service.checkIn(11L, localDate, checkedAt,
            ZoneId.of("Asia/Ho_Chi_Minh"), "v1", "v1");

        assertThat(result.profile().getCheckinStreak()).isEqualTo(5);
        assertThat(result.profile().getLongestStreak()).isEqualTo(5);
    }

    @Test
    void checkInAfterGapResetsCurrentStreakAndKeepsLongestStreak() {
        Date checkedAt = Date.from(Instant.parse("2026-07-30T03:00:00Z"));
        LocalDate localDate = LocalDate.of(2026, 7, 30);
        GamificationProfileRow profile = profile(null, 4L, null);
        profile.setLastCheckinDate(localDate.minusDays(2));
        profile.setCheckinStreak(4);
        profile.setLongestStreak(7);
        when(mapper.lockProfile(11L)).thenReturn(profile);
        when(mapper.updateCheckIn(11L, 4L, localDate, 1, 7)).thenReturn(1);

        CheckInResult result = service.checkIn(11L, localDate, checkedAt,
            ZoneId.of("Asia/Ho_Chi_Minh"), "v1", "v1");

        assertThat(result.profile().getCheckinStreak()).isEqualTo(1);
        assertThat(result.profile().getLongestStreak()).isEqualTo(7);
    }

    @Test
    void sameDayCheckInReplaysWithoutWritingProfileOrEvent() {
        Date checkedAt = Date.from(Instant.parse("2026-07-30T03:00:00Z"));
        LocalDate localDate = LocalDate.of(2026, 7, 30);
        GamificationProfileRow profile = profile(null, 4L, null);
        profile.setLastCheckinDate(localDate);
        profile.setCheckinStreak(3);
        profile.setLongestStreak(5);
        when(mapper.lockProfile(11L)).thenReturn(profile);

        CheckInResult result = service.checkIn(11L, localDate, checkedAt,
            ZoneId.of("Asia/Ho_Chi_Minh"), "v1", "v1");

        assertThat(result.alreadyCheckedIn()).isTrue();
        assertThat(result.profile()).isSameAs(profile);
        verify(mapper, never()).updateCheckIn(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt());
        verify(eventRecorder, never()).ingest(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsCheckInWhenLocalDateDoesNotMatchTimestampZone() {
        Date checkedAt = Date.from(Instant.parse("2026-07-30T17:00:00Z"));

        assertThatThrownBy(() -> service.checkIn(11L, LocalDate.of(2026, 7, 30), checkedAt,
            ZoneId.of("Asia/Ho_Chi_Minh"), "v1", "v1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Ngày hoặc chính sách điểm danh không hợp lệ");

        verify(mapper, never()).insertProfileIgnore(11L, "v1");
    }

    @Test
    void rejectsCheckInOlderThanProfileHistory() {
        Date checkedAt = Date.from(Instant.parse("2026-07-29T03:00:00Z"));
        GamificationProfileRow profile = profile(null, 4L, null);
        profile.setLastCheckinDate(LocalDate.of(2026, 7, 30));
        when(mapper.lockProfile(11L)).thenReturn(profile);

        assertThatThrownBy(() -> service.checkIn(11L, LocalDate.of(2026, 7, 29), checkedAt,
            ZoneId.of("Asia/Ho_Chi_Minh"), "v1", "v1"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("cũ hơn lịch sử hồ sơ");

        verify(mapper, never()).updateCheckIn(
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyInt(),
            org.mockito.ArgumentMatchers.anyInt());
        verify(eventRecorder, never()).ingest(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void listsQuestProgressUsingDailyAndIsoWeeklyKeys() {
        QuestProgressRow row = new QuestProgressRow();
        row.setQuestCode("DAILY_PAID_CHAPTER");
        Date observedAt = Date.from(Instant.parse("2027-01-01T03:00:00Z"));
        when(mapper.selectActiveQuestCampaigns(observedAt)).thenReturn(List.of());
        when(mapper.selectQuestProgress(11L, "2027-01-01", "2026-W53", "DEFAULT"))
            .thenReturn(List.of(row));

        assertThat(service.listQuests(11L, LocalDate.of(2027, 1, 1), observedAt))
            .containsExactly(row);
    }

    @Test
    void listsQuestRewardsFromCampaignActiveAtServerTime() {
        Date observedAt = Date.from(Instant.parse("2027-01-01T03:00:00Z"));
        QuestCampaignRow campaign = campaign("TET_2027", "v2");
        QuestProgressRow row = new QuestProgressRow();
        row.setQuestCode("DAILY_PAID_CHAPTER");
        when(mapper.selectActiveQuestCampaigns(observedAt)).thenReturn(List.of(campaign));
        when(mapper.selectQuestProgress(11L, "2027-01-01", "2026-W53", "TET_2027"))
            .thenReturn(List.of(row));

        assertThat(service.listQuests(11L, LocalDate.of(2027, 1, 1), observedAt))
            .containsExactly(row);
    }

    @Test
    void rejectsOverlappingActiveQuestCampaigns() {
        Date observedAt = Date.from(Instant.parse("2027-01-01T03:00:00Z"));
        when(mapper.selectActiveQuestCampaigns(observedAt)).thenReturn(List.of(
            campaign("TET_2027", "v2"), campaign("NEW_YEAR_2027", "v2")));

        assertThatThrownBy(() -> service.listQuests(
            11L, LocalDate.of(2027, 1, 1), observedAt))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("chồng lấn");
    }

    @Test
    void claimsCompletedQuestIntoExpTicketLedgersAndEmitsLevelEvent() {
        Date claimedAt = Date.from(Instant.parse("2026-07-30T03:00:00Z"));
        QuestDefinitionRow quest = quest("DAILY_READING", "DAILY", 30);
        quest.setActive(true);
        QuestProgressRow progress = new QuestProgressRow();
        progress.setCurrentCount(30);
        progress.setTargetCount(30);
        progress.setCompletedAt(claimedAt);
        GamificationProfileRow profile = profile(null, 0L, null);
        profile.setLevel(1);
        profile.setTotalExp(90L);
        LevelRuleRow levelRule = new LevelRuleRow();
        levelRule.setLevel(2);
        levelRule.setFrameCode("frame_bronze");
        UserExpLedgerRow expLedger = new UserExpLedgerRow();
        expLedger.setId(71L);
        TicketLedgerRow ticketLedger = new TicketLedgerRow();
        ticketLedger.setId(72L);
        QuestClaimRow claim = new QuestClaimRow();
        claim.setQuestCode("DAILY_READING");
        claim.setPeriodKey("2026-07-30");
        claim.setExpAmount(20L);
        claim.setTicketAmount(1L);
        LevelRuleRow next = new LevelRuleRow();
        next.setMinExp(300L);
        TicketAccountRow account = new TicketAccountRow();
        account.setAvailableBalance(4L);
        when(mapper.selectQuestByCode("DAILY_READING")).thenReturn(quest);
        when(mapper.selectQuestClaim(11L, "DAILY_READING", "2026-07-30"))
            .thenReturn(null, null, claim);
        when(mapper.lockQuestProgress(11L, "DAILY_READING", "2026-07-30"))
            .thenReturn(progress);
        when(mapper.selectActiveQuestCampaigns(claimedAt))
            .thenReturn(List.of(campaign("SUMMER_2026", "v2")));
        when(mapper.selectQuestRewardSummary("DAILY_READING", "SUMMER_2026"))
            .thenReturn(new QuestRewardSummary(0L, 1L));
        when(mapper.selectQuestRewardSummary("DAILY_READING", "DEFAULT"))
            .thenReturn(new QuestRewardSummary(20L, 0L));
        when(mapper.lockProfile(11L)).thenReturn(profile);
        when(mapper.selectLevelRuleForExp("v1", 110L)).thenReturn(levelRule);
        when(mapper.selectLevelRulesBetween("v1", 1, 2)).thenReturn(List.of(levelRule));
        when(mapper.insertExpLedger(11L, "QUEST_EXP:11:DAILY_READING:2026-07-30",
            "QUEST", 20L, 110L, "v1", "v2")).thenReturn(1);
        when(mapper.selectExpLedgerBySourceKey("QUEST_EXP:11:DAILY_READING:2026-07-30"))
            .thenReturn(expLedger);
        when(mapper.updateProfileExp(11L, 0L, 110L, 2, "frame_bronze")).thenReturn(1);
        when(monthlyTicketService.grant(org.mockito.ArgumentMatchers.any(TicketGrantCommand.class)))
            .thenReturn(TicketPostResult.POSTED);
        when(monthlyTicketMapper.selectLedgerByIdempotencyKey(
            "QUEST_TICKET:11:DAILY_READING:2026-07-30")).thenReturn(ticketLedger);
        when(mapper.insertQuestClaim(11L, "DAILY_READING", "2026-07-30", "SUMMER_2026",
            20L, 1L, 71L, 72L, "QUEST_CLAIM:11:DAILY_READING:2026-07-30", "v2"))
            .thenReturn(1);
        when(mapper.selectNextLevelRule("v1", 110L)).thenReturn(next);
        when(monthlyTicketService.getOrCreateAccount(11L)).thenReturn(account);
        QuestClaimCommand command = new QuestClaimCommand(11L, "DAILY_READING",
            LocalDate.of(2026, 7, 30), claimedAt, ZoneId.of("Asia/Ho_Chi_Minh"), 60,
            "v1", "v1");

        QuestClaimResult result = service.claimQuest(command);

        assertThat(result.alreadyClaimed()).isFalse();
        assertThat(result.profile().profile().getTotalExp()).isEqualTo(110L);
        assertThat(result.profile().profile().getLevel()).isEqualTo(2);
        assertThat(result.ticketBalance()).isEqualTo(4L);
        ArgumentCaptor<TicketGrantCommand> ticketCommand =
            ArgumentCaptor.forClass(TicketGrantCommand.class);
        verify(monthlyTicketService).grant(ticketCommand.capture());
        assertThat(ticketCommand.getValue().sourceType()).isEqualTo("QUEST");
        assertThat(ticketCommand.getValue().idempotencyKey())
            .isEqualTo("QUEST_TICKET:11:DAILY_READING:2026-07-30");
        assertThat(ticketCommand.getValue().policyVersion()).isEqualTo("v2");
        ArgumentCaptor<GamificationEventInput> levelEvent =
            ArgumentCaptor.forClass(GamificationEventInput.class);
        verify(eventRecorder).ingest(levelEvent.capture());
        assertThat(levelEvent.getValue().eventType()).isEqualTo("LEVEL_REACHED");
        assertThat(levelEvent.getValue().sourceKey()).isEqualTo("GAMIFY:LEVEL_REACHED:11:2:v1");
    }

    @Test
    void replaysExistingClaimWithoutPostingRewardsAgain() {
        QuestDefinitionRow quest = quest("DAILY_PAID_CHAPTER", "DAILY", 1);
        quest.setActive(false);
        QuestClaimRow claim = new QuestClaimRow();
        claim.setQuestCode("DAILY_PAID_CHAPTER");
        claim.setPeriodKey("2026-07-30");
        when(mapper.selectQuestByCode("DAILY_PAID_CHAPTER")).thenReturn(quest);
        when(mapper.selectQuestClaim(11L, "DAILY_PAID_CHAPTER", "2026-07-30"))
            .thenReturn(claim);
        when(mapper.selectProfile(11L)).thenReturn(profile(null, 1L, null));
        TicketAccountRow account = new TicketAccountRow();
        account.setAvailableBalance(3L);
        when(monthlyTicketService.getOrCreateAccount(11L)).thenReturn(account);

        QuestClaimResult result = service.claimQuest(new QuestClaimCommand(11L,
            "DAILY_PAID_CHAPTER", LocalDate.of(2026, 7, 30), new Date(),
            ZoneId.of("Asia/Ho_Chi_Minh"), 60, "v1", "v1"));

        assertThat(result.alreadyClaimed()).isTrue();
        verify(mapper, never()).lockQuestProgress(11L, "DAILY_PAID_CHAPTER", "2026-07-30");
        verify(eventRecorder, never()).ingest(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void rejectsClaimWhenProgressIsNotCompleted() {
        QuestDefinitionRow quest = quest("DAILY_PAID_CHAPTER", "DAILY", 1);
        quest.setActive(true);
        QuestProgressRow progress = new QuestProgressRow();
        progress.setCurrentCount(0);
        progress.setTargetCount(1);
        when(mapper.selectQuestByCode("DAILY_PAID_CHAPTER")).thenReturn(quest);
        when(mapper.lockQuestProgress(11L, "DAILY_PAID_CHAPTER", "2026-07-30"))
            .thenReturn(progress);

        assertThatThrownBy(() -> service.claimQuest(new QuestClaimCommand(11L,
            "DAILY_PAID_CHAPTER", LocalDate.of(2026, 7, 30), new Date(),
            ZoneId.of("Asia/Ho_Chi_Minh"), 60, "v1", "v1")))
            .isInstanceOf(BusinessException.class);
    }

    private GamificationEventRow event(String eventType, LocalDate localDate, Date occurredAt) {
        GamificationEventRow event = new GamificationEventRow();
        event.setUserId(11L);
        event.setEventType(eventType);
        event.setLocalDate(localDate);
        event.setOccurredAt(occurredAt);
        return event;
    }

    private QuestDefinitionRow quest(String code, String periodType, int targetCount) {
        QuestDefinitionRow quest = new QuestDefinitionRow();
        quest.setQuestCode(code);
        quest.setPeriodType(periodType);
        quest.setTargetCount(targetCount);
        return quest;
    }

    private QuestCampaignRow campaign(String code, String policyVersion) {
        QuestCampaignRow row = new QuestCampaignRow();
        row.setCampaignCode(code);
        row.setPolicyVersion(policyVersion);
        row.setStatus("ACTIVE");
        return row;
    }

    @Test
    void togglesTickerOptOutWithVersionAndAudit() {
        GamificationProfileRow locked = profile("NHAP_MON", 4L, null);
        locked.setTickerOptOut(true);
        GamificationProfileRow updated = profile("NHAP_MON", 5L, null);
        updated.setTickerOptOut(false);
        when(mapper.lockProfile(11L)).thenReturn(locked);
        when(mapper.updateTickerOptOut(11L, false, 4L)).thenReturn(1);
        when(mapper.insertProfileAudit(11L, "TICKER_OPT", "true", "false",
            "USER", 11L, "Người dùng đổi hiển thị bảng chạy")).thenReturn(1);
        when(mapper.selectProfile(11L)).thenReturn(updated);

        GamificationProfileRow result = service.updateTickerOptOut(11L, false, 4L, "v1");

        assertThat(result).isSameAs(updated);
        verify(mapper).updateTickerOptOut(11L, false, 4L);
    }

    @Test
    void tickerOptOutIsNoOpWhenValueAlreadyMatches() {
        GamificationProfileRow locked = profile("NHAP_MON", 4L, null);
        locked.setTickerOptOut(true);
        when(mapper.lockProfile(11L)).thenReturn(locked);

        GamificationProfileRow result = service.updateTickerOptOut(11L, true, 999L, "v1");

        assertThat(result).isSameAs(locked);
        verify(mapper, never()).updateTickerOptOut(anyLong(), anyBoolean(), anyLong());
    }

    @Test
    void rejectsStaleVersionBeforeTogglingTicker() {
        GamificationProfileRow locked = profile("NHAP_MON", 5L, null);
        locked.setTickerOptOut(true);
        when(mapper.lockProfile(11L)).thenReturn(locked);

        assertThatThrownBy(() -> service.updateTickerOptOut(11L, false, 4L, "v1"))
            .isInstanceOf(BusinessException.class);

        verify(mapper, never()).updateTickerOptOut(anyLong(), anyBoolean(), anyLong());
    }

    @Test
    void adminModerationHidesUserAndAuditsAsAdmin() {
        GamificationProfileRow locked = profile("NHAP_MON", 4L, null);
        locked.setTickerOptOut(false);
        GamificationProfileRow updated = profile("NHAP_MON", 5L, null);
        updated.setTickerOptOut(true);
        when(mapper.lockProfile(11L)).thenReturn(locked);
        when(mapper.updateTickerOptOut(11L, true, 4L)).thenReturn(1);
        when(mapper.insertProfileAudit(11L, "TICKER_OPT", "false", "true",
            "ADMIN", 9L, "Nickname vi phạm quy định cộng đồng")).thenReturn(1);
        when(mapper.selectProfile(11L)).thenReturn(updated);

        GamificationProfileRow result = service.adminSetTickerOptOut(11L, true, 9L,
            "Nickname vi phạm quy định cộng đồng", "v1");

        assertThat(result).isSameAs(updated);
        verify(mapper).updateTickerOptOut(11L, true, 4L);
        verify(mapper).insertProfileAudit(11L, "TICKER_OPT", "false", "true",
            "ADMIN", 9L, "Nickname vi phạm quy định cộng đồng");
    }

    @Test
    void adminModerationIsNoOpWhenAlreadyHidden() {
        GamificationProfileRow locked = profile("NHAP_MON", 4L, null);
        locked.setTickerOptOut(true);
        when(mapper.lockProfile(11L)).thenReturn(locked);

        GamificationProfileRow result = service.adminSetTickerOptOut(11L, true, 9L,
            "Nickname vi phạm quy định cộng đồng", "v1");

        assertThat(result).isSameAs(locked);
        verify(mapper, never()).updateTickerOptOut(anyLong(), anyBoolean(), anyLong());
        verify(mapper, never()).insertProfileAudit(anyLong(), anyString(), anyString(),
            anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void adminModerationRejectsShortReason() {
        GamificationProfileRow locked = profile("NHAP_MON", 4L, null);
        locked.setTickerOptOut(false);
        when(mapper.lockProfile(11L)).thenReturn(locked);

        assertThatThrownBy(() -> service.adminSetTickerOptOut(11L, true, 9L, "quá ngắn", "v1"))
            .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void adminModerationRejectsMissingOperator() {
        assertThatThrownBy(() -> service.adminSetTickerOptOut(11L, true, 0L,
            "Nickname vi phạm quy định cộng đồng", "v1"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(mapper, never()).lockProfile(anyLong());
    }

    private GamificationProfileRow profile(String realmCode, long version, Date changedAt) {
        GamificationProfileRow row = new GamificationProfileRow();
        row.setUserId(11L);
        row.setLevel(3);
        row.setTotalExp(250L);
        row.setRuleVersion("v1");
        row.setRealmCode(realmCode);
        row.setRealmChangedAt(changedAt);
        row.setVersion(version);
        return row;
    }
}
