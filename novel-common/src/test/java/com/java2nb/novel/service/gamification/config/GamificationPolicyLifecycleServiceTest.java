package com.java2nb.novel.service.gamification.config;

import com.java2nb.novel.mapper.GamificationPolicyBundleMapper;
import com.java2nb.novel.service.gamification.LevelRuleRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class GamificationPolicyLifecycleServiceTest {
    private GamificationPolicyBundleMapper mapper;
    private GamificationPolicyHasher hasher;
    private GamificationPolicyLifecycleService service;

    @BeforeEach
    void setUp() {
        mapper = mock(GamificationPolicyBundleMapper.class);
        hasher = mock(GamificationPolicyHasher.class);
        service = new GamificationPolicyLifecycleService(mapper, hasher,
            Clock.fixed(Instant.parse("2026-08-10T03:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void creatorCannotApproveOwnPolicy() {
        GamificationPolicyBundleRow row = bundle("PENDING_APPROVAL", 3L, 9L);
        when(mapper.lockBundle("v2")).thenReturn(row);

        assertThatThrownBy(() -> service.approve("v2", 3L, 9L,
            "Phê duyệt policy do chính mình tạo"))
            .isInstanceOf(SecurityException.class);

        verify(mapper, never()).approve(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void policyVersionUsesSameLowercaseContractAsRuntimeConfig() {
        assertThatThrownBy(() -> service.get("V2"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Phiên bản policy không hợp lệ");
    }

    @Test
    void savingDraftContentRefreshesHashAndOptimisticVersion() {
        GamificationPolicyBundleRow locked = bundle("DRAFT", 3L, 9L);
        GamificationPolicyBundleRow updated = bundle("DRAFT", 4L, 9L);
        updated.setContentHash("b".repeat(64));
        when(mapper.lockBundle("v2")).thenReturn(locked);
        when(mapper.upsertLevel(org.mockito.ArgumentMatchers.eq("v2"),
            org.mockito.ArgumentMatchers.any(LevelRuleRow.class))).thenReturn(1);
        when(mapper.selectCanonicalLines("v2")).thenReturn(List.of("10|0000000001|0|level.1|frame"));
        when(hasher.hash(org.mockito.ArgumentMatchers.anyList())).thenReturn("b".repeat(64));
        when(mapper.updateDraftHash(11L, 3L, "b".repeat(64),
            "Điều chỉnh ngưỡng level đầu tiên")).thenReturn(1);
        when(mapper.insertAudit(11L, "CONTENT_UPDATED", "DRAFT", "DRAFT", 15L,
            "Điều chỉnh ngưỡng level đầu tiên", "a".repeat(64), "b".repeat(64)))
            .thenReturn(1);
        stubDetail(updated);
        LevelRuleRow level = new LevelRuleRow();
        level.setLevel(1);
        level.setMinExp(0L);
        level.setTitleKey("level.1");
        level.setFrameCode("frame");

        GamificationPolicyBundleDetail result = service.saveLevel(" v2 ", 3L, level, 15L,
            "Điều chỉnh ngưỡng level đầu tiên");

        assertThat(result.getBundle().getVersion()).isEqualTo(4L);
        verify(mapper).upsertLevel(org.mockito.ArgumentMatchers.eq("v2"),
            org.mockito.ArgumentMatchers.same(level));
        verify(mapper).updateDraftHash(11L, 3L, "b".repeat(64),
            "Điều chỉnh ngưỡng level đầu tiên");
    }

    private void stubDetail(GamificationPolicyBundleRow row) {
        when(mapper.selectBundle("v2")).thenReturn(row);
        when(mapper.selectLevels("v2")).thenReturn(List.of());
        when(mapper.selectLevelRewards("v2")).thenReturn(List.of());
        when(mapper.selectQuests("v2")).thenReturn(List.of());
        when(mapper.selectQuestRewards("v2")).thenReturn(List.of());
        when(mapper.selectRealms("v2")).thenReturn(List.of());
        when(mapper.selectAbuseRules("v2")).thenReturn(List.of());
    }

    private GamificationPolicyBundleRow bundle(String status, long version, long createdBy) {
        GamificationPolicyBundleRow row = new GamificationPolicyBundleRow();
        row.setId(11L);
        row.setPolicyVersion("v2");
        row.setStatus(status);
        row.setVersion(version);
        row.setCreatedBy(createdBy);
        row.setContentHash("a".repeat(64));
        return row;
    }
}
