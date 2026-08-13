package com.java2nb.novel.service.gamification.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "novel.gamification")
public class GamificationEnvironmentProperties {
    private String policyVersion = "v1";
    private String zoneId = "Asia/Ho_Chi_Minh";
    private Event event = new Event();
    private Ticket ticket = new Ticket();
    private Vote vote = new Vote();
    private Quest quest = new Quest();
    private Realm realm = new Realm();
    private Season season = new Season();
    private Reward reward = new Reward();
    private Job job = new Job();

    public GamificationConfigSnapshot toSnapshot(String voteIpHashKeyId) {
        rejectLockedPolicyFlags();
        return GamificationConfigSnapshot.builder()
            .runtimeRevision(0L).policyVersion(policyVersion).zoneId(zoneId)
            .eventEnabled(event.enabled).eventDrainBatchSize(event.drainBatchSize)
            .eventDrainDelayMs(event.drainDelayMs).eventMaxAttempt(event.maxAttempt)
            .ticketEnabled(ticket.enabled).ticketLotValidityDays(ticket.lotValidityDays)
            .ticketExpiryCron(ticket.expiryCron).ticketExpiryBatchSize(ticket.expiryBatchSize)
            .ticketMaxGrantPerBatch(ticket.maxGrantPerBatch)
            .voteEnabled(vote.enabled).voteAllowCrawledBooks(vote.allowCrawledBooks)
            .voteIpHashKeyId(voteIpHashKeyId)
            .voteMaxTicketsPerRequest(vote.maxTicketsPerRequest)
            .voteMaxVotesPerDay(vote.maxVotesPerDay)
            .voteMaxTicketsPerDay(vote.maxTicketsPerDay)
            .voteMaxTicketsPerBookPerSeason(vote.maxTicketsPerBookPerSeason)
            .voteMaxLotsPerSpend(vote.maxLotsPerSpend)
            .questEnabled(quest.enabled)
            .questHeartbeatIntervalSeconds(quest.heartbeatIntervalSeconds)
            .questHeartbeatMaxMinutesPerDay(quest.heartbeatMaxMinutesPerDay)
            .realmEnabled(realm.enabled).realmChangeCooldownHours(realm.changeCooldownHours)
            .seasonEnabled(season.enabled).seasonCloseCron(season.closeCron)
            .seasonCloseDrainSeconds(season.closeDrainSeconds)
            .seasonResumeDelayMs(season.resumeDelayMs)
            .seasonReviewWindowHours(season.reviewWindowHours)
            .rewardEnabled(reward.enabled).rewardClaimWindowDays(reward.claimWindowDays)
            .rewardReleaseCron(reward.releaseCron).jobLeaseSeconds(job.leaseSeconds)
            .jobBatchSize(job.batchSize).build();
    }

    private void rejectLockedPolicyFlags() {
        if (ticket.grantOnTopUpEnabled) {
            throw new IllegalArgumentException("GAMIFICATION_TICKET_GRANT_ON_TOPUP bị khóa false theo policy v1");
        }
        if (quest.replyQuestEnabled) {
            throw new IllegalArgumentException("GAMIFICATION_QUEST_REPLY_ENABLED bị khóa false theo policy v1");
        }
        if (realm.affectsBenefits) {
            throw new IllegalArgumentException("GAMIFICATION_REALM_AFFECTS_BENEFITS bị khóa false theo policy v1");
        }
        if (season.autoFinalize) {
            throw new IllegalArgumentException("GAMIFICATION_SEASON_AUTO_FINALIZE bị khóa false theo policy v1");
        }
    }

    @Data
    public static class Event {
        private boolean enabled;
        private int drainBatchSize = 200;
        private long drainDelayMs = 15_000;
        private int maxAttempt = 10;
    }

    @Data
    public static class Ticket {
        private boolean enabled;
        private int lotValidityDays = 60;
        private boolean grantOnTopUpEnabled;
        private String expiryCron = "0 20 3 * * ?";
        private int expiryBatchSize = 500;
        private int maxGrantPerBatch = 1_000;
    }

    @Data
    public static class Vote {
        private boolean enabled;
        private boolean allowCrawledBooks;
        private int maxTicketsPerRequest = 10;
        private int maxVotesPerDay = 20;
        private int maxTicketsPerDay = 50;
        private int maxTicketsPerBookPerSeason = 100;
        private int maxLotsPerSpend = 50;
    }

    @Data
    public static class Quest {
        private boolean enabled;
        private boolean replyQuestEnabled;
        private int heartbeatIntervalSeconds = 60;
        private int heartbeatMaxMinutesPerDay = 180;
    }

    @Data
    public static class Realm {
        private boolean enabled;
        private boolean affectsBenefits;
        private int changeCooldownHours = 24;
    }

    @Data
    public static class Season {
        private boolean enabled;
        private String closeCron = "0 5 0 1 * ?";
        private int closeDrainSeconds = 60;
        private long resumeDelayMs = 30_000;
        private boolean autoFinalize;
        private int reviewWindowHours = 72;
    }

    @Data
    public static class Reward {
        private boolean enabled;
        private int claimWindowDays = 7;
        private String releaseCron = "0 40 3 * * ?";
    }

    @Data
    public static class Job {
        private int leaseSeconds = 300;
        private int batchSize = 500;
    }
}
