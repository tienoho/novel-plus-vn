package com.java2nb.novel.core.schedule;

import com.java2nb.novel.service.gamification.AuthorRewardService;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorRewardReleaseScheduleTest {

    @Test
    void releasesOnlyAllocationsOlderThanTheConfiguredClaimWindow() {
        Instant now = Instant.parse("2026-09-08T00:00:00Z");
        GamificationConfigProvider provider = mock(GamificationConfigProvider.class);
        when(provider.currentForWrite()).thenReturn(
            GamificationConfigSnapshot.bootstrapDisabled().toBuilder().rewardEnabled(true).build());
        AuthorRewardService service = mock(AuthorRewardService.class);
        when(service.listMaturedAllocationIds(Date.from(now), 500)).thenReturn(List.of(81L, 82L));
        AuthorRewardReleaseSchedule schedule = new AuthorRewardReleaseSchedule(provider, service,
            Clock.fixed(now, ZoneOffset.UTC));

        schedule.releaseMaturedRewards();

        verify(service).releaseMaturedReward(81L, Date.from(now));
        verify(service).releaseMaturedReward(82L, Date.from(now));
    }
}
