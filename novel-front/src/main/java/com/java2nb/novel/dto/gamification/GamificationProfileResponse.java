package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.GamificationProfileRow;
import com.java2nb.novel.service.gamification.GamificationProfileSnapshot;

import java.time.LocalDate;
import java.util.Date;

public record GamificationProfileResponse(int level, long totalExp, Long nextLevelExp,
                                          String realmCode, String frameCode, int checkinStreak,
                                          int longestStreak, LocalDate lastCheckinDate,
                                          Date realmChangedAt, boolean tickerOptOut, long version) {
    public static GamificationProfileResponse from(GamificationProfileSnapshot snapshot) {
        return from(snapshot.profile(), snapshot.nextLevelExp());
    }

    public static GamificationProfileResponse from(GamificationProfileRow profile, Long nextLevelExp) {
        return new GamificationProfileResponse(profile.getLevel(), profile.getTotalExp(), nextLevelExp,
            profile.getRealmCode(), profile.getFrameCode(), profile.getCheckinStreak(),
            profile.getLongestStreak(), profile.getLastCheckinDate(), profile.getRealmChangedAt(),
            Boolean.TRUE.equals(profile.getTickerOptOut()), profile.getVersion());
    }
}
