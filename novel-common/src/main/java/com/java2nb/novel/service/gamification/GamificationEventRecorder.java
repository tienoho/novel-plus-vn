package com.java2nb.novel.service.gamification;

public interface GamificationEventRecorder {

    GamificationEventPostResult ingest(GamificationEventInput event);
}
