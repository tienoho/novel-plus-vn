package com.java2nb.novel.service.gamification;

import java.util.Date;

public interface GamificationEventService {

    void ingest(String eventType, String sourceKey, long userId, Long bookId,
                Date occurredAt, String payloadJson);
}
