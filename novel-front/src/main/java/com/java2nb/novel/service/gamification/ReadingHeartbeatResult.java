package com.java2nb.novel.service.gamification;

import java.util.List;

public record ReadingHeartbeatResult(String sessionId, int acceptedSeconds,
                                     int verifiedMinutesToday, boolean capReached,
                                     int nextSequence, boolean replayed,
                                     List<String> eventSourceKeys) {
    public ReadingHeartbeatResult {
        eventSourceKeys = List.copyOf(eventSourceKeys);
    }
}
