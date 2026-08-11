package com.java2nb.novel.service.gamification;

import com.java2nb.novel.mapper.GamificationProgressMapper;
import com.java2nb.novel.service.impl.GamificationEventFailureWriter;
import com.java2nb.novel.service.impl.GamificationEventProcessor;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
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
    private final Clock clock;

    @Autowired
    public GamificationReadingHeartbeatService(ReadingHeartbeatWriter writer,
                                               GamificationProgressMapper mapper,
                                               GamificationEventProcessor processor,
                                               GamificationEventFailureWriter failureWriter) {
        this(writer, mapper, processor, failureWriter, Clock.systemUTC());
    }

    GamificationReadingHeartbeatService(ReadingHeartbeatWriter writer,
                                        GamificationProgressMapper mapper,
                                        GamificationEventProcessor processor,
                                        GamificationEventFailureWriter failureWriter, Clock clock) {
        this.writer = writer;
        this.mapper = mapper;
        this.processor = processor;
        this.failureWriter = failureWriter;
        this.clock = clock;
    }

    public ReadingHeartbeatResult record(long userId, ReadingHeartbeatInput input,
                                         GamificationConfigSnapshot config) {
        Instant now = clock.instant();
        Date heartbeatAt = Date.from(now);
        ZoneId zoneId = ZoneId.of(config.getZoneId());
        LocalDate localDate = LocalDate.ofInstant(now, zoneId);
        ReadingHeartbeatResult result = writer.record(new ReadingHeartbeatCommand(
            userId, input.sessionId(), input.bookId(), input.bookIndexId(), input.sequence(),
            input.activeSeconds(), heartbeatAt, localDate, zoneId,
            config.getQuestHeartbeatIntervalSeconds(),
            config.getQuestHeartbeatMaxMinutesPerDay(), config.getPolicyVersion(),
            config.getRuntimeRevision()));
        for (String sourceKey : result.eventSourceKeys()) {
            processEvent(sourceKey, heartbeatAt, config);
        }
        return result;
    }

    private void processEvent(String sourceKey, Date processedAt,
                              GamificationConfigSnapshot config) {
        GamificationEventRow event = mapper.selectEventBySourceKey(sourceKey);
        if (event == null) {
            throw new IllegalStateException("Không tìm thấy event phút đọc vừa ghi");
        }
        try {
            EventProcessResult processResult = processor.process(event.getId(), processedAt,
                config.getEventMaxAttempt());
            if (processResult == EventProcessResult.SKIPPED) {
                throw new IllegalStateException("Event phút đọc không khớp nhiệm vụ đọc đang hoạt động");
            }
        } catch (RuntimeException exception) {
            failureWriter.record(event.getId(), processedAt, exception,
                config.getEventMaxAttempt());
            throw exception;
        }
    }
}
