package com.java2nb.novel.service.gamification.config;

import com.java2nb.novel.mapper.GamificationRuntimeConfigMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GamificationRuntimeConfigLifecycleServiceTest {

    private static final Clock CLOCK = Clock.fixed(
        Instant.parse("2026-08-10T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void highRiskDraftCannotBeApprovedByItsCreator() {
        GamificationRuntimeConfigMapper mapper = mock(GamificationRuntimeConfigMapper.class);
        GamificationRuntimeConfigRow pending = row(2L, "PENDING_APPROVAL", true, 100L, 0L);
        when(mapper.lockById(2L)).thenReturn(pending);
        GamificationRuntimeConfigLifecycleService service = service(mapper);

        assertThatThrownBy(() -> service.approve(2L, 0L, 100L, "Tự duyệt cấu hình"))
            .isInstanceOf(SecurityException.class)
            .hasMessageContaining("không được tự phê duyệt");
        verify(mapper, never()).approve(any(Long.class), any(Long.class), any(Long.class), any(Date.class));
    }

    @Test
    void staleDraftSaveIsRejectedInsteadOfOverwriting() {
        GamificationRuntimeConfigMapper mapper = mock(GamificationRuntimeConfigMapper.class);
        GamificationRuntimeConfigRow draft = row(2L, "DRAFT", false, 100L, 3L);
        when(mapper.lockById(2L)).thenReturn(draft);
        GamificationRuntimeConfigLifecycleService service = service(mapper);

        assertThatThrownBy(() -> service.saveDraft(2L, 2L,
            GamificationConfigSnapshot.bootstrapDisabled(), 100L, "Điều chỉnh batch"))
            .isInstanceOf(GamificationConfigConflictException.class);
        verify(mapper, never()).saveDraft(any(GamificationRuntimeConfigRow.class), any(Long.class));
    }

    @Test
    void highRiskSubmitPersistsStrictestActivationBoundary() {
        GamificationRuntimeConfigMapper mapper = mock(GamificationRuntimeConfigMapper.class);
        GamificationRuntimeConfigRow active = row(1L, "ACTIVE", false, 1L, 0L);
        GamificationRuntimeConfigRow draft = row(2L, "DRAFT", false, 100L, 0L);
        draft.setSnapshot(GamificationConfigSnapshot.bootstrapDisabled().toBuilder()
            .ticketEnabled(true).voteEnabled(true).build());
        when(mapper.lockById(2L)).thenReturn(draft);
        when(mapper.selectActive()).thenReturn(active);
        when(mapper.isPolicyPublished("v1")).thenReturn(true);
        when(mapper.submit(eq(2L), eq(0L), eq("NEXT_SEASON"), eq(true), eq(100L), any(Date.class),
            eq("Bật bỏ phiếu cho kỳ tiếp theo"))).thenReturn(1);
        when(mapper.insertAudit(eq(2L), eq("SUBMITTED"), eq("DRAFT"),
            eq("PENDING_APPROVAL"), eq(100L), eq("Bật bỏ phiếu cho kỳ tiếp theo"),
            any(), any(), any())).thenReturn(1);
        GamificationRuntimeConfigLifecycleService service = service(mapper);

        service.submit(2L, 0L, 100L, "Bật bỏ phiếu cho kỳ tiếp theo");

        verify(mapper).submit(eq(2L), eq(0L), eq("NEXT_SEASON"), eq(true), eq(100L),
            any(Date.class), eq("Bật bỏ phiếu cho kỳ tiếp theo"));
    }

    @Test
    void questSubmitRejectsHeartbeatCapBelowPublishedReadingTarget() {
        GamificationRuntimeConfigMapper mapper = mock(GamificationRuntimeConfigMapper.class);
        GamificationRuntimeConfigRow draft = row(2L, "DRAFT", false, 100L, 0L);
        draft.setSnapshot(GamificationConfigSnapshot.bootstrapDisabled().toBuilder()
            .eventEnabled(true).questEnabled(true).questHeartbeatMaxMinutesPerDay(20).build());
        when(mapper.lockById(2L)).thenReturn(draft);
        when(mapper.isPolicyPublished("v1")).thenReturn(true);
        when(mapper.selectActiveReadingQuestTarget("v1")).thenReturn(30);

        assertThatThrownBy(() -> service(mapper).submit(2L, 0L, 100L,
            "Bật nhiệm vụ đọc với trần chưa hợp lệ"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("mục tiêu nhiệm vụ đọc");
        verify(mapper, never()).submit(anyLong(), anyLong(), anyString(), anyBoolean(), anyLong(),
            any(Date.class), anyString());
    }

    @Test
    void rewardSubmitRequiresPublishedRulesAndApprovedCampaign() {
        GamificationRuntimeConfigMapper mapper = mock(GamificationRuntimeConfigMapper.class);
        GamificationRuntimeConfigRow draft = row(2L, "DRAFT", false, 100L, 0L);
        draft.setSnapshot(GamificationConfigSnapshot.bootstrapDisabled().toBuilder()
            .seasonEnabled(true).rewardEnabled(true).build());
        when(mapper.lockById(2L)).thenReturn(draft);
        when(mapper.isPolicyPublished("v1")).thenReturn(true);

        assertThatThrownBy(() -> service(mapper).submit(2L, 0L, 100L,
            "Bật thưởng khi chưa đủ điều kiện công khai"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("luật chơi công khai");

        when(mapper.hasPublishedPublicPolicy("v1")).thenReturn(true);
        assertThatThrownBy(() -> service(mapper).submit(2L, 0L, 100L,
            "Bật thưởng khi chưa có campaign được duyệt"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("campaign");
    }

    @Test
    void scheduleCannotCrossNextDayBoundaryEarly() {
        GamificationRuntimeConfigMapper mapper = mock(GamificationRuntimeConfigMapper.class);
        GamificationRuntimeConfigRow approved = row(2L, "APPROVED", true, 100L, 0L);
        approved.setActivationClass("NEXT_DAY");
        when(mapper.lockById(2L)).thenReturn(approved);

        assertThatThrownBy(() -> service(mapper).schedule(2L, 0L, 200L,
            "Hẹn áp dụng quota theo ngày", Date.from(Instant.parse("2026-08-10T16:59:59Z"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("NEXT_DAY");
        verify(mapper, never()).schedule(anyLong(), anyLong(), anyLong(), any(Date.class),
            anyString());
    }

    @Test
    void scheduleCannotActivateNextSeasonChangeBeforeOpenSeasonEnds() {
        GamificationRuntimeConfigMapper mapper = mock(GamificationRuntimeConfigMapper.class);
        GamificationRuntimeConfigRow approved = row(2L, "APPROVED", true, 100L, 0L);
        approved.setActivationClass("NEXT_SEASON");
        when(mapper.lockById(2L)).thenReturn(approved);
        when(mapper.selectLatestOpenSeasonEnd())
            .thenReturn(Date.from(Instant.parse("2026-09-01T00:00:00Z")));

        assertThatThrownBy(() -> service(mapper).schedule(2L, 0L, 200L,
            "Hẹn áp dụng từ kỳ kế tiếp", Date.from(Instant.parse("2026-08-31T23:59:59Z"))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("NEXT_SEASON");
    }

    @Test
    void dueActivationRevalidatesSecretBeforeArchivingCurrentRevision() {
        GamificationRuntimeConfigMapper mapper = mock(GamificationRuntimeConfigMapper.class);
        GamificationRuntimeConfigRow active = row(1L, "ACTIVE", false, 1L, 0L);
        GamificationRuntimeConfigRow scheduled = row(2L, "SCHEDULED", true, 100L, 0L);
        scheduled.setSnapshot(GamificationConfigSnapshot.bootstrapDisabled().toBuilder()
            .ticketEnabled(true).voteEnabled(true).build());
        when(mapper.lockDueScheduled(any(Date.class))).thenReturn(scheduled);
        when(mapper.selectActive()).thenReturn(active);
        when(mapper.isPolicyPublished("v1")).thenReturn(true);
        when(mapper.archiveActive(1L, 0L, Date.from(CLOCK.instant()))).thenReturn(1);
        when(mapper.activateScheduled(2L, 0L, 200L, Date.from(CLOCK.instant()))).thenReturn(1);

        GamificationRuntimeConfigLifecycleService service = new GamificationRuntimeConfigLifecycleService(
            mapper, new GamificationConfigValidator(), new GamificationConfigHasher(),
            requiredKeyId -> false, CLOCK);

        assertThatThrownBy(() -> service.activateDue(200L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("vote.hashSecret.required");
        verify(mapper, never()).archiveActive(anyLong(), anyLong(), any(Date.class));
        verify(mapper, never()).activateScheduled(anyLong(), anyLong(), anyLong(), any(Date.class));
    }

    private GamificationRuntimeConfigLifecycleService service(GamificationRuntimeConfigMapper mapper) {
        return new GamificationRuntimeConfigLifecycleService(mapper,
            new GamificationConfigValidator(), new GamificationConfigHasher(),
            requiredKeyId -> true, CLOCK);
    }

    private GamificationRuntimeConfigRow row(long id, String status, boolean highRisk,
                                              long createdBy, long version) {
        GamificationRuntimeConfigRow row = new GamificationRuntimeConfigRow();
        row.setId(id);
        row.setStatus(status);
        row.setHighRisk(highRisk);
        row.setCreatedBy(createdBy);
        row.setVersion(version);
        row.setConfigHash("before");
        row.setSnapshot(GamificationConfigSnapshot.bootstrapDisabled());
        return row;
    }
}
