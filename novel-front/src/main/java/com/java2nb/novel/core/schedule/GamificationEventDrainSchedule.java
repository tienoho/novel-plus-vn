package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.GamificationProperties;
import com.java2nb.novel.core.observability.NovelBusinessMetrics;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.GamificationQueue;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.Outcome;
import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.service.impl.GamificationEventFailureWriter;
import com.java2nb.novel.service.impl.GamificationEventProcessor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.util.Date;

@Component
@Slf4j
public class GamificationEventDrainSchedule {
    private final GamificationProperties properties;
    private final GamificationProgressMapper mapper;
    private final GamificationEventProcessor processor;
    private final GamificationEventFailureWriter failureWriter;
    private final NovelBusinessMetrics metrics;
    private final Clock clock;

    @Autowired
    public GamificationEventDrainSchedule(GamificationProperties properties,
                                          GamificationProgressMapper mapper,
                                          GamificationEventProcessor processor,
                                          GamificationEventFailureWriter failureWriter,
                                          NovelBusinessMetrics metrics) {
        this(properties, mapper, processor, failureWriter, metrics, Clock.systemUTC());
    }

    GamificationEventDrainSchedule(GamificationProperties properties,
                                   GamificationProgressMapper mapper,
                                   GamificationEventProcessor processor,
                                   GamificationEventFailureWriter failureWriter,
                                   NovelBusinessMetrics metrics,
                                   Clock clock) {
        this.properties = properties;
        this.mapper = mapper;
        this.processor = processor;
        this.failureWriter = failureWriter;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${novel.gamification.event.drain-delay-ms:15000}")
    public void drain() {
        if (!properties.getEvent().isEnabled() || !properties.getQuest().isEnabled()
            || !properties.isConfigured()) {
            return;
        }
        int maxAttempt = properties.getEvent().getMaxAttempt();
        metrics.setGamificationQueue(GamificationQueue.PENDING_EVENTS,
            mapper.countPendingEvents(maxAttempt));
        for (Long eventId : mapper.selectPendingEventIds(
            properties.getEvent().getDrainBatchSize(), maxAttempt)) {
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
