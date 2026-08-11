package com.java2nb.novel.service.gamification.config;

@FunctionalInterface
public interface GamificationSecretReadiness {
    boolean isReady(String requiredKeyId);
}
