package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.GamificationProperties;
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
    private final Clock clock;

    @Autowired
    public GamificationEventDrainSchedule(GamificationProperties properties,
                                          GamificationProgressMapper mapper,
                                          GamificationEventProcessor processor,
                                          GamificationEventFailureWriter failureWriter) {
        this(properties, mapper, processor, failureWriter, Clock.systemUTC());
    }

    GamificationEventDrainSchedule(GamificationProperties properties,
                                   GamificationProgressMapper mapper,
                                   GamificationEventProcessor processor,
                                   GamificationEventFailureWriter failureWriter,
                                   Clock clock) {
        this.properties = properties;
        this.mapper = mapper;
        this.processor = processor;
        this.failureWriter = failureWriter;
        this.clock = clock;
    }

    @Scheduled(fixedDelayString = "${novel.gamification.event.drain-delay-ms:15000}")
    public void drain() {
        if (!properties.getEvent().isEnabled() || !properties.getQuest().isEnabled()
            || !properties.isConfigured()) {
            return;
        }
        int maxAttempt = properties.getEvent().getMaxAttempt();
        for (Long eventId : mapper.selectPendingEventIds(
            properties.getEvent().getDrainBatchSize(), maxAttempt)) {
            Date now = Date.from(clock.instant());
            try {
                processor.process(eventId, now, maxAttempt);
            } catch (RuntimeException exception) {
                failureWriter.record(eventId, now, exception, maxAttempt);
                log.error("GAMIFY-ALERT-009 không thể xử lý event gamification: eventId={}",
                    eventId, exception);
            }
        }
    }
}
