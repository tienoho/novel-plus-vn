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
public class GamificationCheckInService {

    private final GamificationProgressService progressService;
    private final GamificationProgressMapper mapper;
    private final GamificationEventProcessor eventProcessor;
    private final GamificationEventFailureWriter failureWriter;
    private final Clock clock;

    @Autowired
    public GamificationCheckInService(GamificationProgressService progressService,
                                      GamificationProgressMapper mapper,
                                      GamificationEventProcessor eventProcessor,
                                      GamificationEventFailureWriter failureWriter) {
        this(progressService, mapper, eventProcessor, failureWriter, Clock.systemUTC());
    }

    GamificationCheckInService(GamificationProgressService progressService,
                               GamificationProgressMapper mapper,
                               GamificationEventProcessor eventProcessor,
                               GamificationEventFailureWriter failureWriter, Clock clock) {
        this.progressService = progressService;
        this.mapper = mapper;
        this.eventProcessor = eventProcessor;
        this.failureWriter = failureWriter;
        this.clock = clock;
    }

    public CheckInOutcome checkIn(long userId, GamificationConfigSnapshot config) {
        Instant now = clock.instant();
        Date checkedAt = Date.from(now);
        ZoneId zoneId = ZoneId.of(config.getZoneId());
        LocalDate localDate = LocalDate.ofInstant(now, zoneId);
        String version = config.getPolicyVersion();
        CheckInResult checkIn = progressService.checkIn(
            userId, localDate, checkedAt, zoneId, version, version,
            config.getRuntimeRevision());
        GamificationEventRow event = mapper.selectEventBySourceKey(checkIn.sourceKey());
        if (event == null) {
            throw new IllegalStateException("Không tìm thấy event điểm danh vừa ghi");
        }
        try {
            EventProcessResult processResult = eventProcessor.process(event.getId(), checkedAt,
                config.getEventMaxAttempt());
            if (processResult == EventProcessResult.SKIPPED) {
                throw new IllegalStateException("Event điểm danh không khớp nhiệm vụ đang hoạt động");
            }
        } catch (RuntimeException exception) {
            failureWriter.record(event.getId(), checkedAt, exception,
                config.getEventMaxAttempt());
            throw exception;
        }
        QuestClaimResult reward = progressService.claimQuest(new QuestClaimCommand(userId,
            "DAILY_CHECK_IN", localDate, checkedAt, zoneId,
            config.getTicketLotValidityDays(), version, version, config.getRuntimeRevision()));
        Date nextCheckIn = Date.from(localDate.plusDays(1).atStartOfDay(zoneId).toInstant());
        return new CheckInOutcome(checkIn, reward, nextCheckIn);
    }
}
