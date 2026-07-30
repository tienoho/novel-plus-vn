package com.java2nb.novel.service.gamification;

public record ReadingHeartbeatInput(String sessionId, long bookId, long bookIndexId,
                                    int sequence, int activeSeconds) {
}
