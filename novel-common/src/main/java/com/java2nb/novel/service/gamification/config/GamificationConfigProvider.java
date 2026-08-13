package com.java2nb.novel.service.gamification.config;

public interface GamificationConfigProvider {
    GamificationConfigSnapshot current();

    GamificationConfigSnapshot currentForWrite();

    void refresh();
}
