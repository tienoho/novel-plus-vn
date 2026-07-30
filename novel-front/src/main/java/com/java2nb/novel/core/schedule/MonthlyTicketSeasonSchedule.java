package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.GamificationProperties;
import com.java2nb.novel.service.gamification.MonthlyRankingService;
import com.java2nb.novel.service.gamification.MonthlySeasonRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Date;
import java.util.UUID;

/** Duy trì kỳ thường và điều phối claim; mọi luật snapshot nằm trong MonthlyRankingService. */
@Component
@Slf4j
public class MonthlyTicketSeasonSchedule {

    private static final int SEASON_SCAN_LIMIT = 100;

    private final GamificationProperties properties;
    private final MonthlyRankingService monthlyRankingService;
    private final Clock clock;
    private final String ownerInstance;

    @Autowired
    public MonthlyTicketSeasonSchedule(GamificationProperties properties,
                                       MonthlyRankingService monthlyRankingService) {
        this(properties, monthlyRankingService, Clock.systemUTC(),
            "season-" + UUID.randomUUID());
    }

    MonthlyTicketSeasonSchedule(GamificationProperties properties,
                                MonthlyRankingService monthlyRankingService,
                                Clock clock, String ownerInstance) {
        this.properties = properties;
        this.monthlyRankingService = monthlyRankingService;
        this.clock = clock;
        this.ownerInstance = ownerInstance;
    }

    @Scheduled(
        cron = "${novel.gamification.season.close-cron:0 5 0 1 * ?}",
        zone = "${novel.gamification.zone-id:Asia/Ho_Chi_Minh}"
    )
    public void closeDueSeasons() {
        if (!isEnabled()) {
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

    @Scheduled(
        fixedDelayString = "${novel.gamification.season.resume-delay-ms:30000}",
        initialDelayString = "${novel.gamification.season.resume-delay-ms:30000}"
    )
    public void maintainAndResume() {
        if (!isEnabled()) {
            return;
        }
        Date now = Date.from(clock.instant());
        monthlyRankingService.ensureRegularSeason(
            now, properties.resolveZoneId(), properties.getPolicyVersion());
        claimDueSeasons(now);
        for (MonthlySeasonRow season : monthlyRankingService.listClosingSeasons(SEASON_SCAN_LIMIT)) {
            try {
                monthlyRankingService.buildSnapshot(season.getId(), ownerInstance, now,
                    properties.getSeason().getCloseDrainSeconds(),
                    properties.getJob().getLeaseSeconds(),
                    properties.getJob().getBatchSize());
            } catch (RuntimeException exception) {
                log.error("GAMIFY-ALERT-005 snapshot kỳ Ngọn Đuốc thất bại: seasonId={}",
                    season.getId(), exception);
            }
        }
    }

    private boolean isEnabled() {
        return properties.getSeason().isEnabled() && properties.isConfigured();
    }
}
