package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.GamificationProperties;
import com.java2nb.novel.service.gamification.AuthorRewardService;
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
        GamificationProperties properties = new GamificationProperties();
        properties.getReward().setEnabled(true);
        properties.getReward().setClaimWindowDays(7);
        AuthorRewardService service = mock(AuthorRewardService.class);
        Date cutoff = Date.from(Instant.parse("2026-09-01T00:00:00Z"));
        when(service.listMaturedAllocationIds(cutoff, 500)).thenReturn(List.of(81L, 82L));
        AuthorRewardReleaseSchedule schedule = new AuthorRewardReleaseSchedule(properties, service,
            Clock.fixed(now, ZoneOffset.UTC));

        schedule.releaseMaturedRewards();

        verify(service).releaseMaturedReward(81L, Date.from(now));
        verify(service).releaseMaturedReward(82L, Date.from(now));
    }
}
