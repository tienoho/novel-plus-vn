package com.java2nb.novel.service.gamification.config;

import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
@JsonDeserialize(builder = GamificationConfigSnapshot.GamificationConfigSnapshotBuilder.class)
public class GamificationConfigSnapshot {
    long runtimeRevision;
    String policyVersion;
    String zoneId;

    boolean eventEnabled;
    int eventDrainBatchSize;
    long eventDrainDelayMs;
    int eventMaxAttempt;

    boolean ticketEnabled;
    int ticketLotValidityDays;
    String ticketExpiryCron;
    int ticketExpiryBatchSize;
    int ticketMaxGrantPerBatch;

    boolean voteEnabled;
    boolean voteAllowCrawledBooks;
    String voteIpHashKeyId;
    int voteMaxTicketsPerRequest;
    int voteMaxVotesPerDay;
    int voteMaxTicketsPerDay;
    int voteMaxTicketsPerBookPerSeason;
    int voteMaxLotsPerSpend;

    boolean questEnabled;
    int questHeartbeatIntervalSeconds;
    int questHeartbeatMaxMinutesPerDay;

    boolean realmEnabled;
    int realmChangeCooldownHours;

    boolean seasonEnabled;
    String seasonCloseCron;
    int seasonCloseDrainSeconds;
    long seasonResumeDelayMs;
    int seasonReviewWindowHours;

    boolean rewardEnabled;
    int rewardClaimWindowDays;
    String rewardReleaseCron;

    int jobLeaseSeconds;
    int jobBatchSize;

    public static GamificationConfigSnapshot bootstrapDisabled() {
        return builder()
            .runtimeRevision(1L)
            .policyVersion("v1")
            .zoneId("Asia/Ho_Chi_Minh")
            .eventDrainBatchSize(200)
            .eventDrainDelayMs(15_000)
            .eventMaxAttempt(10)
            .ticketLotValidityDays(60)
            .ticketExpiryCron("0 20 3 * * ?")
            .ticketExpiryBatchSize(500)
            .ticketMaxGrantPerBatch(1_000)
            .voteIpHashKeyId("v1")
            .voteMaxTicketsPerRequest(10)
            .voteMaxVotesPerDay(20)
            .voteMaxTicketsPerDay(50)
            .voteMaxTicketsPerBookPerSeason(100)
            .voteMaxLotsPerSpend(50)
            .questHeartbeatIntervalSeconds(60)
            .questHeartbeatMaxMinutesPerDay(180)
            .realmChangeCooldownHours(24)
            .seasonCloseCron("0 5 0 1 * ?")
            .seasonCloseDrainSeconds(60)
            .seasonResumeDelayMs(30_000)
            .seasonReviewWindowHours(72)
            .rewardClaimWindowDays(7)
            .rewardReleaseCron("0 40 3 * * ?")
            .jobLeaseSeconds(300)
            .jobBatchSize(500)
            .build();
    }

    public boolean anyWriteEnabled() {
        return eventEnabled || ticketEnabled || voteEnabled || questEnabled || realmEnabled
            || seasonEnabled || rewardEnabled;
    }

    @JsonPOJOBuilder(withPrefix = "")
    public static class GamificationConfigSnapshotBuilder {
    }
}
