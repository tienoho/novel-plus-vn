package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.observability.NovelBusinessMetrics;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.GamificationQueue;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.Outcome;
import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.service.impl.GamificationEventFailureWriter;
import com.java2nb.novel.service.impl.GamificationEventProcessor;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Date;

@Component
@Slf4j
public class GamificationEventDrainSchedule {
    private final GamificationConfigProvider configProvider;
    private final GamificationProgressMapper mapper;
    private final GamificationEventProcessor processor;
    private final GamificationEventFailureWriter failureWriter;
    private final NovelBusinessMetrics metrics;
    private final Clock clock;

    @Autowired
    public GamificationEventDrainSchedule(GamificationConfigProvider configProvider,
                                          GamificationProgressMapper mapper,
                                          GamificationEventProcessor processor,
                                          GamificationEventFailureWriter failureWriter,
                                          NovelBusinessMetrics metrics) {
        this(configProvider, mapper, processor, failureWriter, metrics, Clock.systemUTC());
    }

    GamificationEventDrainSchedule(GamificationConfigProvider configProvider,
                                   GamificationProgressMapper mapper,
                                   GamificationEventProcessor processor,
                                   GamificationEventFailureWriter failureWriter,
                                   NovelBusinessMetrics metrics,
                                   Clock clock) {
        this.configProvider = configProvider;
        this.mapper = mapper;
        this.processor = processor;
        this.failureWriter = failureWriter;
        this.metrics = metrics;
        this.clock = clock;
    }

    public void drain() {
        GamificationConfigSnapshot config = configProvider.currentForWrite();
        if (!config.isEventEnabled() || !config.isQuestEnabled()) {
            return;
        }
        int maxAttempt = config.getEventMaxAttempt();
        metrics.setGamificationQueue(GamificationQueue.PENDING_EVENTS,
            mapper.countPendingEvents(maxAttempt));
        for (Long eventId : mapper.selectPendingEventIds(
            config.getEventDrainBatchSize(), maxAttempt)) {
            Date now = Date.from(clock.instant());
            try {
                switch (processor.process(eventId, now, maxAttempt)) {
                    case PROCESSED -> metrics.recordGamificationEvent(Outcome.SUCCESS);
                    case SKIPPED -> metrics.recordGamificationEvent(Outcome.ALREADY_PROCESSED);
                    case NOT_OWNER -> metrics.recordGamificationEvent(Outcome.REJECTED);
                }
            } catch (RuntimeException exception) {
                metrics.recordGamificationEvent(Outcome.FAILED);
                failureWriter.record(eventId, now, exception, maxAttempt);
                log.error("GAMIFY-ALERT-009 không thể xử lý event gamification: eventId={}",
                    eventId, exception);
            }
        }
        metrics.setGamificationQueue(GamificationQueue.PENDING_EVENTS,
            mapper.countPendingEvents(maxAttempt));
    }
}
