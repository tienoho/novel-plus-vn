package com.java2nb.novel.service.gamification;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public record ReadingHeartbeatCommand(long userId, String sessionId, long bookId, long bookIndexId,
                                      int sequence, int activeSeconds, Date heartbeatAt,
                                      LocalDate localDate, ZoneId zoneId, int intervalSeconds,
                                      int maxMinutesPerDay, String policyVersion) {
}
