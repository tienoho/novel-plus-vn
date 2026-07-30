package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.GamificationProperties;
import com.java2nb.novel.service.gamification.MonthlyRankingService;
import com.java2nb.novel.service.gamification.MonthlySeasonRow;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlyTicketSeasonScheduleTest {

    private static final Instant NOW = Instant.parse("2027-01-01T00:00:00Z");

    @Test
    void maintainsCurrentSeasonAndResumesEveryClosingSeason() {
        GamificationProperties properties = enabledProperties();
        MonthlyRankingService service = mock(MonthlyRankingService.class);
        MonthlySeasonRow closing = new MonthlySeasonRow();
        closing.setId(91L);
        MonthlySeasonRow overdue = new MonthlySeasonRow();
        overdue.setId(90L);
        Date now = Date.from(NOW);
        when(service.listSeasonsReadyToClose(now, 100)).thenReturn(List.of(overdue));
        when(service.listClosingSeasons(100)).thenReturn(List.of(closing));
        MonthlyTicketSeasonSchedule schedule = new MonthlyTicketSeasonSchedule(properties, service,
            Clock.fixed(NOW, ZoneOffset.UTC), "season-test-owner");

        schedule.maintainAndResume();

        verify(service).ensureRegularSeason(now, properties.resolveZoneId(), "v1");
        verify(service).closeSeason(90L, now);
        verify(service).buildSnapshot(91L, "season-test-owner", now, 60, 300, 500);
    }

    @Test
    void closeCronClaimsEveryDueSeasonThroughTheService() {
        GamificationProperties properties = enabledProperties();
        MonthlyRankingService service = mock(MonthlyRankingService.class);
        MonthlySeasonRow due = new MonthlySeasonRow();
        due.setId(92L);
        Date now = Date.from(NOW);
        when(service.listSeasonsReadyToClose(now, 100)).thenReturn(List.of(due));
        MonthlyTicketSeasonSchedule schedule = new MonthlyTicketSeasonSchedule(properties, service,
            Clock.fixed(NOW, ZoneOffset.UTC), "season-test-owner");

        schedule.closeDueSeasons();

        verify(service).closeSeason(92L, now);
    }

    private GamificationProperties enabledProperties() {
        GamificationProperties properties = new GamificationProperties();
        properties.getSeason().setEnabled(true);
        return properties;
    }
}
