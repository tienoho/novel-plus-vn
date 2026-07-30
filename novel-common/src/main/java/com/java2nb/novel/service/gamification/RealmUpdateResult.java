package com.java2nb.novel.service.gamification;

import java.util.Date;

public record RealmUpdateResult(GamificationProfileRow profile, Date cooldownUntil) {
    public RealmUpdateResult {
        cooldownUntil = cooldownUntil == null ? null : new Date(cooldownUntil.getTime());
    }

    @Override
    public Date cooldownUntil() {
        return cooldownUntil == null ? null : new Date(cooldownUntil.getTime());
    }
}
