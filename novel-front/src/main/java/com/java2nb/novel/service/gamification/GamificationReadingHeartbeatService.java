package com.java2nb.novel.service.gamification;

import com.java2nb.novel.core.config.GamificationProperties;
import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.service.impl.GamificationEventFailureWriter;
import com.java2nb.novel.service.impl.GamificationEventProcessor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

@Service
public class GamificationReadingHeartbeatService {

    private final ReadingHeartbeatWriter writer;
    private final GamificationProgressMapper mapper;
    private final GamificationEventProcessor processor;
    private final GamificationEventFailureWriter failureWriter;
    private final GamificationProperties properties;
    private final Clock clock;

    @Autowired
    public GamificationReadingHeartbeatService(ReadingHeartbeatWriter writer,
                                               GamificationProgressMapper mapper,
                                               GamificationEventProcessor processor,
                                               GamificationEventFailureWriter failureWriter,
                                               GamificationProperties properties) {
        this(writer, mapper, processor, failureWriter, properties, Clock.systemUTC());
    }

    GamificationReadingHeartbeatService(ReadingHeartbeatWriter writer,
                                        GamificationProgressMapper mapper,
                                        GamificationEventProcessor processor,
                                        GamificationEventFailureWriter failureWriter,
                                        GamificationProperties properties, Clock clock) {
        this.writer = writer;
        this.mapper = mapper;
        this.processor = processor;
        this.failureWriter = failureWriter;
        this.properties = properties;
        this.clock = clock;
    }

    public ReadingHeartbeatResult record(long userId, ReadingHeartbeatInput input) {
        Instant now = clock.instant();
        Date heartbeatAt = Date.from(now);
        ZoneId zoneId = properties.resolveZoneId();
        LocalDate localDate = LocalDate.ofInstant(now, zoneId);
        ReadingHeartbeatResult result = writer.record(new ReadingHeartbeatCommand(
            userId, input.sessionId(), input.bookId(), input.bookIndexId(), input.sequence(),
            input.activeSeconds(), heartbeatAt, localDate, zoneId,
            properties.getQuest().getHeartbeatIntervalSeconds(),
            properties.getQuest().getHeartbeatMaxMinutesPerDay(), properties.getPolicyVersion()));
        for (String sourceKey : result.eventSourceKeys()) {
            processEvent(sourceKey, heartbeatAt);
        }
        return result;
    }

    private void processEvent(String sourceKey, Date processedAt) {
        GamificationEventRow event = mapper.selectEventBySourceKey(sourceKey);
        if (event == null) {
            throw new IllegalStateException("Không tìm thấy event phút đọc vừa ghi");
        }
        try {
            EventProcessResult processResult = processor.process(event.getId(), processedAt,
                properties.getEvent().getMaxAttempt());
            if (processResult == EventProcessResult.SKIPPED) {
                throw new IllegalStateException("Event phút đọc không khớp nhiệm vụ đọc đang hoạt động");
            }
        } catch (RuntimeException exception) {
            failureWriter.record(event.getId(), processedAt, exception,
                properties.getEvent().getMaxAttempt());
            throw exception;
        }
    }
}
