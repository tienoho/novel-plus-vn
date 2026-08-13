package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.RealmUpdateResult;
import com.java2nb.novel.service.gamification.GamificationProfileSnapshot;

import java.util.Date;

public record RealmUpdateResponse(GamificationProfileResponse profile, Date cooldownUntil) {
    public static RealmUpdateResponse from(RealmUpdateResult result,
                                           GamificationProfileSnapshot snapshot) {
        return new RealmUpdateResponse(GamificationProfileResponse.from(snapshot), result.cooldownUntil());
    }
}
