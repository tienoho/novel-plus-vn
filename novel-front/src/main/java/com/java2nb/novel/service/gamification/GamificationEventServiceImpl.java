package com.java2nb.novel.service.gamification;

import com.java2nb.novel.service.gamification.GamificationEventInputFactory;
import com.java2nb.novel.service.gamification.GamificationEventRecorder;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class GamificationEventServiceImpl implements GamificationEventService {

    private final GamificationConfigProvider configProvider;
    private final GamificationEventRecorder recorder;

    public GamificationEventServiceImpl(GamificationConfigProvider configProvider,
                                        GamificationEventRecorder recorder) {
        this.configProvider = configProvider;
        this.recorder = recorder;
    }

    @Override
    public void ingest(String eventType, String sourceKey, long userId, Long bookId,
                       Date occurredAt, String payloadJson) {
        GamificationConfigSnapshot config = configProvider.currentForWrite();
        if (!config.isEventEnabled()) {
            return;
        }
        recorder.ingest(GamificationEventInputFactory.create(eventType, sourceKey, userId, bookId,
            occurredAt, payloadJson, java.time.ZoneId.of(config.getZoneId()),
            config.getPolicyVersion(), config.getRuntimeRevision()));
    }
}
