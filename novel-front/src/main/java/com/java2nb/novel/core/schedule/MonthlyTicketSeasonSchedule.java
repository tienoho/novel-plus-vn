package com.java2nb.novel.core.schedule;

import com.java2nb.novel.service.gamification.MonthlyRankingService;
import com.java2nb.novel.service.gamification.MonthlySeasonRow;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Date;
import java.util.UUID;

/** Duy trì kỳ thường và điều phối claim; mọi luật snapshot nằm trong MonthlyRankingService. */
@Component
@Slf4j
public class MonthlyTicketSeasonSchedule {

    private static final int SEASON_SCAN_LIMIT = 100;

    private final GamificationConfigProvider configProvider;
    private final MonthlyRankingService monthlyRankingService;
    private final Clock clock;
    private final String ownerInstance;

    @Autowired
    public MonthlyTicketSeasonSchedule(GamificationConfigProvider configProvider,
                                       MonthlyRankingService monthlyRankingService) {
        this(configProvider, monthlyRankingService, Clock.systemUTC(),
            "season-" + UUID.randomUUID());
    }

    MonthlyTicketSeasonSchedule(GamificationConfigProvider configProvider,
                                MonthlyRankingService monthlyRankingService,
                                Clock clock, String ownerInstance) {
        this.configProvider = configProvider;
        this.monthlyRankingService = monthlyRankingService;
        this.clock = clock;
        this.ownerInstance = ownerInstance;
    }

    public void closeDueSeasons() {
        GamificationConfigSnapshot config = configProvider.currentForWrite();
        if (!config.isSeasonEnabled()) {
            return;
        }
        Date now = Date.from(clock.instant());
        claimDueSeasons(now);
    }

    private void claimDueSeasons(Date now) {
        for (MonthlySeasonRow season : monthlyRankingService.listSeasonsReadyToClose(
            now, SEASON_SCAN_LIMIT)) {
            try {
                monthlyRankingService.closeSeason(season.getId(), now);
            } catch (RuntimeException exception) {
                log.error("GAMIFY-ALERT-005 không thể claim đóng kỳ Ngọn Đuốc: seasonId={}",
                    season.getId(), exception);
            }
        }
    }

    public void maintainAndResume() {
        GamificationConfigSnapshot config = configProvider.currentForWrite();
        if (!config.isSeasonEnabled()) {
            return;
        }
        Date now = Date.from(clock.instant());
        monthlyRankingService.ensureRegularSeason(
            now, java.time.ZoneId.of(config.getZoneId()), config.getPolicyVersion(),
            config.getRuntimeRevision());
        claimDueSeasons(now);
        for (MonthlySeasonRow season : monthlyRankingService.listClosingSeasons(SEASON_SCAN_LIMIT)) {
            try {
                monthlyRankingService.buildSnapshot(season.getId(), ownerInstance, now,
                    config.getSeasonCloseDrainSeconds(), config.getJobLeaseSeconds(),
                    config.getJobBatchSize());
            } catch (RuntimeException exception) {
                log.error("GAMIFY-ALERT-005 snapshot kỳ Ngọn Đuốc thất bại: seasonId={}",
                    season.getId(), exception);
            }
        }
    }

}
