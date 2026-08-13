package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.ReadingHeartbeatResult;

public record ReadingHeartbeatResponse(String sessionId, int acceptedSeconds,
                                       int verifiedMinutesToday, boolean capReached,
                                       int nextSequence, boolean replayed) {
    public static ReadingHeartbeatResponse from(ReadingHeartbeatResult result) {
        return new ReadingHeartbeatResponse(result.sessionId(), result.acceptedSeconds(),
            result.verifiedMinutesToday(), result.capReached(), result.nextSequence(),
            result.replayed());
    }
}
