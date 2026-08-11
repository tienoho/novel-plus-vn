package com.java2nb.novel.service.gamification.config;

import java.util.Set;

public record GamificationConfigDiffView(
    Set<String> changedKeys,
    GamificationActivationClass activationClass,
    boolean highRisk,
    GamificationConfigSnapshot before,
    GamificationConfigSnapshot after,
    long earliestEffectiveAtMillis
) {
    public static GamificationConfigDiffView of(GamificationConfigDiff diff,
                                                 GamificationConfigSnapshot before,
                                                 GamificationConfigSnapshot after,
                                                 long earliestEffectiveAtMillis) {
        return new GamificationConfigDiffView(diff.changedKeys(), diff.activationClass(),
            diff.highRisk(), before, after, earliestEffectiveAtMillis);
    }
}
