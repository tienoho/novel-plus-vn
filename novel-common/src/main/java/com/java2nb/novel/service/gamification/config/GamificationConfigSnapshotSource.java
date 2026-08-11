package com.java2nb.novel.service.gamification.config;

@FunctionalInterface
public interface GamificationConfigSnapshotSource {
    GamificationConfigSnapshot loadActive();
}
