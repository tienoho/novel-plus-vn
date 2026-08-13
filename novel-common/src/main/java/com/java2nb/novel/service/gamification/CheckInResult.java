package com.java2nb.novel.service.gamification;

public record CheckInResult(GamificationProfileRow profile, String sourceKey,
                            boolean alreadyCheckedIn) {
}
