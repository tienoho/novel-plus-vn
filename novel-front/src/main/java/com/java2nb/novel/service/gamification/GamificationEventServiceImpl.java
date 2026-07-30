package com.java2nb.novel.service.gamification;

import com.java2nb.novel.core.config.GamificationProperties;
import com.java2nb.novel.service.gamification.GamificationEventInputFactory;
import com.java2nb.novel.service.gamification.GamificationEventRecorder;
import org.springframework.stereotype.Service;

import java.util.Date;

@Service
public class GamificationEventServiceImpl implements GamificationEventService {

    private final GamificationProperties properties;
    private final GamificationEventRecorder recorder;

    public GamificationEventServiceImpl(GamificationProperties properties,
                                        GamificationEventRecorder recorder) {
        this.properties = properties;
        this.recorder = recorder;
    }

    @Override
    public void ingest(String eventType, String sourceKey, long userId, Long bookId,
                       Date occurredAt, String payloadJson) {
        if (!properties.getEvent().isEnabled()) {
            return;
        }
        if (!properties.isConfigured()) {
            throw new IllegalStateException("Cấu hình gamification không hợp lệ khi ghi sự kiện");
        }
        recorder.ingest(GamificationEventInputFactory.create(eventType, sourceKey, userId, bookId,
            occurredAt, payloadJson, properties.resolveZoneId(), properties.getPolicyVersion()));
    }
}
