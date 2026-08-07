package com.java2nb.novel.service.gamification;

public interface LevelRewardService {

    /** Trả 1 khi event level có policy và đã được cấp hoặc replay hợp lệ; ngược lại trả 0. */
    int apply(GamificationEventRow event);
}
