package com.java2nb.novel.core.schedule;

import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledFuture;

@Slf4j
@Component
public class GamificationDynamicScheduler {

    private final TaskScheduler scheduler;
    private final GamificationConfigProvider provider;
    private final GamificationEventDrainSchedule eventDrain;
    private final MonthlyTicketExpirySchedule ticketExpiry;
    private final MonthlyTicketSeasonSchedule season;
    private final AuthorRewardReleaseSchedule rewardRelease;
    private final Counter rescheduleFailures;
    private final List<ScheduledFuture<?>> futures = new ArrayList<>();
    private volatile long scheduledRevision = -1;

    public GamificationDynamicScheduler(TaskScheduler scheduler, GamificationConfigProvider provider,
                                        GamificationEventDrainSchedule eventDrain,
                                        MonthlyTicketExpirySchedule ticketExpiry,
                                        MonthlyTicketSeasonSchedule season,
                                        AuthorRewardReleaseSchedule rewardRelease,
                                        MeterRegistry registry) {
        this.scheduler = scheduler;
        this.provider = provider;
        this.eventDrain = eventDrain;
        this.ticketExpiry = ticketExpiry;
        this.season = season;
        this.rewardRelease = rewardRelease;
        this.rescheduleFailures = Counter.builder("gamification.scheduler.reschedule.failures")
            .description("Số lần đăng ký lại lịch gamification thất bại")
            .register(registry);
        Gauge.builder("gamification.scheduler.revision", this,
                GamificationDynamicScheduler::scheduledRevision)
            .description("Revision đã được dynamic scheduler đăng ký")
            .register(registry);
    }

    @PostConstruct
    void initialize() {
        rescheduleSafely();
    }

    @Scheduled(fixedDelayString = "${GAMIFICATION_CONFIG_REFRESH_MS:2000}")
    public void watchRevision() {
        rescheduleSafely();
    }

    synchronized void rescheduleIfChanged() {
        GamificationConfigSnapshot config = provider.current();
        if (scheduledRevision == config.getRuntimeRevision()) {
            return;
        }
        cancelFutureRuns();
        ZoneId zone = ZoneId.of(config.getZoneId());
        add(scheduler.scheduleWithFixedDelay(eventDrain::drain,
            Duration.ofMillis(config.getEventDrainDelayMs())));
        add(scheduler.schedule(ticketExpiry::expireDueLots,
            new CronTrigger(config.getTicketExpiryCron(), zone)));
        add(scheduler.schedule(season::closeDueSeasons,
            new CronTrigger(config.getSeasonCloseCron(), zone)));
        add(scheduler.scheduleWithFixedDelay(season::maintainAndResume,
            Duration.ofMillis(config.getSeasonResumeDelayMs())));
        add(scheduler.schedule(rewardRelease::releaseMaturedRewards,
            new CronTrigger(config.getRewardReleaseCron(), zone)));
        scheduledRevision = config.getRuntimeRevision();
        log.info("Đã đăng ký lại lịch gamification theo revision {}", scheduledRevision);
    }

    private void rescheduleSafely() {
        try {
            rescheduleIfChanged();
        } catch (RuntimeException exception) {
            rescheduleFailures.increment();
            log.error("Không thể đăng ký lại lịch gamification", exception);
        }
    }

    private double scheduledRevision() {
        return scheduledRevision;
    }

    private void cancelFutureRuns() {
        for (ScheduledFuture<?> future : futures) {
            future.cancel(false);
        }
        futures.clear();
    }

    private void add(ScheduledFuture<?> future) {
        if (future == null) {
            throw new IllegalStateException("TaskScheduler không trả về future cho job gamification");
        }
        futures.add(future);
    }
}
