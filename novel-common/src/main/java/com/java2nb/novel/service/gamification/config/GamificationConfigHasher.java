package com.java2nb.novel.service.gamification.config;

import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class GamificationConfigHasher {

    public String hash(GamificationConfigSnapshot value) {
        String canonical = String.join("\n",
            value.getPolicyVersion(), value.getZoneId(),
            Boolean.toString(value.isEventEnabled()), Integer.toString(value.getEventDrainBatchSize()),
            Long.toString(value.getEventDrainDelayMs()), Integer.toString(value.getEventMaxAttempt()),
            Boolean.toString(value.isTicketEnabled()), Integer.toString(value.getTicketLotValidityDays()),
            value.getTicketExpiryCron(), Integer.toString(value.getTicketExpiryBatchSize()),
            Integer.toString(value.getTicketMaxGrantPerBatch()), Boolean.toString(value.isVoteEnabled()),
            Boolean.toString(value.isVoteAllowCrawledBooks()), value.getVoteIpHashKeyId(),
            Integer.toString(value.getVoteMaxTicketsPerRequest()),
            Integer.toString(value.getVoteMaxVotesPerDay()),
            Integer.toString(value.getVoteMaxTicketsPerDay()),
            Integer.toString(value.getVoteMaxTicketsPerBookPerSeason()),
            Integer.toString(value.getVoteMaxLotsPerSpend()), Boolean.toString(value.isQuestEnabled()),
            Integer.toString(value.getQuestHeartbeatIntervalSeconds()),
            Integer.toString(value.getQuestHeartbeatMaxMinutesPerDay()),
            Boolean.toString(value.isRealmEnabled()),
            Integer.toString(value.getRealmChangeCooldownHours()),
            Boolean.toString(value.isSeasonEnabled()), value.getSeasonCloseCron(),
            Integer.toString(value.getSeasonCloseDrainSeconds()),
            Long.toString(value.getSeasonResumeDelayMs()),
            Integer.toString(value.getSeasonReviewWindowHours()),
            Boolean.toString(value.isRewardEnabled()),
            Integer.toString(value.getRewardClaimWindowDays()), value.getRewardReleaseCron(),
            Integer.toString(value.getJobLeaseSeconds()), Integer.toString(value.getJobBatchSize()));
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", exception);
        }
    }
}
