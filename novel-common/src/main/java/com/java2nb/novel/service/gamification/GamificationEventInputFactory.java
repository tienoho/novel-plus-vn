package com.java2nb.novel.service.gamification;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Date;

public final class GamificationEventInputFactory {

    private GamificationEventInputFactory() {
    }

    public static GamificationEventInput create(String eventType, String sourceKey, long userId,
                                                 Long bookId, Date occurredAt, String payloadJson,
                                                 ZoneId zoneId, String policyVersion) {
        Date eventTime = new Date(occurredAt.getTime());
        LocalDate localDate = Instant.ofEpochMilli(eventTime.getTime()).atZone(zoneId).toLocalDate();
        String canonical = eventType + '|' + sourceKey + '|' + userId + '|'
            + (bookId == null ? "" : bookId) + '|' + localDate + '|'
            + (payloadJson == null ? "" : payloadJson) + '|' + policyVersion;
        return new GamificationEventInput(eventType, sourceKey, userId, bookId, eventTime, localDate,
            sha256(canonical), payloadJson, policyVersion);
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                .digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", exception);
        }
    }
}
