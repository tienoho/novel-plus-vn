package com.java2nb.novel.service.gamification.config;

import lombok.Data;

import java.util.Date;

@Data
public class GamificationRuntimeConfigRow {
    private Long id;
    private Long revisionNo;
    private String revisionCode;
    private Long sourceRevisionId;
    private String sourceHash;
    private String status;
    private String activationClass;
    private Boolean highRisk;
    private String policyVersion;
    private String zoneId;
    private Boolean eventEnabled;
    private Integer eventDrainBatchSize;
    private Long eventDrainDelayMs;
    private Integer eventMaxAttempt;
    private Boolean ticketEnabled;
    private Integer ticketLotValidityDays;
    private String ticketExpiryCron;
    private Integer ticketExpiryBatchSize;
    private Integer ticketMaxGrantPerBatch;
    private Boolean voteEnabled;
    private Boolean voteAllowCrawledBooks;
    private String voteIpHashKeyId;
    private Integer voteMaxTicketsPerRequest;
    private Integer voteMaxVotesPerDay;
    private Integer voteMaxTicketsPerDay;
    private Integer voteMaxTicketsPerBookPerSeason;
    private Integer voteMaxLotsPerSpend;
    private Boolean questEnabled;
    private Integer questHeartbeatIntervalSeconds;
    private Integer questHeartbeatMaxMinutesPerDay;
    private Boolean realmEnabled;
    private Integer realmChangeCooldownHours;
    private Boolean seasonEnabled;
    private String seasonCloseCron;
    private Integer seasonCloseDrainSeconds;
    private Long seasonResumeDelayMs;
    private Integer seasonReviewWindowHours;
    private Boolean rewardEnabled;
    private Integer rewardClaimWindowDays;
    private String rewardReleaseCron;
    private Integer jobLeaseSeconds;
    private Integer jobBatchSize;
    private String configHash;
    private Long createdBy;
    private Long submittedBy;
    private Long approvedBy;
    private Long scheduledBy;
    private Long activatedBy;
    private String changeReason;
    private Date submittedAt;
    private Date approvedAt;
    private Date effectiveAt;
    private Date activatedAt;
    private Date archivedAt;
    private Long version;
    private Date createTime;
    private Date updateTime;

    public GamificationConfigSnapshot getSnapshot() {
        return GamificationConfigSnapshot.builder()
            .runtimeRevision(revisionNo == null ? 0L : revisionNo)
            .policyVersion(policyVersion).zoneId(zoneId)
            .eventEnabled(Boolean.TRUE.equals(eventEnabled))
            .eventDrainBatchSize(orZero(eventDrainBatchSize))
            .eventDrainDelayMs(orZero(eventDrainDelayMs)).eventMaxAttempt(orZero(eventMaxAttempt))
            .ticketEnabled(Boolean.TRUE.equals(ticketEnabled))
            .ticketLotValidityDays(orZero(ticketLotValidityDays)).ticketExpiryCron(ticketExpiryCron)
            .ticketExpiryBatchSize(orZero(ticketExpiryBatchSize))
            .ticketMaxGrantPerBatch(orZero(ticketMaxGrantPerBatch))
            .voteEnabled(Boolean.TRUE.equals(voteEnabled))
            .voteAllowCrawledBooks(Boolean.TRUE.equals(voteAllowCrawledBooks))
            .voteIpHashKeyId(voteIpHashKeyId)
            .voteMaxTicketsPerRequest(orZero(voteMaxTicketsPerRequest))
            .voteMaxVotesPerDay(orZero(voteMaxVotesPerDay))
            .voteMaxTicketsPerDay(orZero(voteMaxTicketsPerDay))
            .voteMaxTicketsPerBookPerSeason(orZero(voteMaxTicketsPerBookPerSeason))
            .voteMaxLotsPerSpend(orZero(voteMaxLotsPerSpend))
            .questEnabled(Boolean.TRUE.equals(questEnabled))
            .questHeartbeatIntervalSeconds(orZero(questHeartbeatIntervalSeconds))
            .questHeartbeatMaxMinutesPerDay(orZero(questHeartbeatMaxMinutesPerDay))
            .realmEnabled(Boolean.TRUE.equals(realmEnabled))
            .realmChangeCooldownHours(orZero(realmChangeCooldownHours))
            .seasonEnabled(Boolean.TRUE.equals(seasonEnabled)).seasonCloseCron(seasonCloseCron)
            .seasonCloseDrainSeconds(orZero(seasonCloseDrainSeconds))
            .seasonResumeDelayMs(orZero(seasonResumeDelayMs))
            .seasonReviewWindowHours(orZero(seasonReviewWindowHours))
            .rewardEnabled(Boolean.TRUE.equals(rewardEnabled))
            .rewardClaimWindowDays(orZero(rewardClaimWindowDays)).rewardReleaseCron(rewardReleaseCron)
            .jobLeaseSeconds(orZero(jobLeaseSeconds)).jobBatchSize(orZero(jobBatchSize)).build();
    }

    public void setSnapshot(GamificationConfigSnapshot value) {
        revisionNo = value.getRuntimeRevision();
        policyVersion = value.getPolicyVersion();
        zoneId = value.getZoneId();
        eventEnabled = value.isEventEnabled();
        eventDrainBatchSize = value.getEventDrainBatchSize();
        eventDrainDelayMs = value.getEventDrainDelayMs();
        eventMaxAttempt = value.getEventMaxAttempt();
        ticketEnabled = value.isTicketEnabled();
        ticketLotValidityDays = value.getTicketLotValidityDays();
        ticketExpiryCron = value.getTicketExpiryCron();
        ticketExpiryBatchSize = value.getTicketExpiryBatchSize();
        ticketMaxGrantPerBatch = value.getTicketMaxGrantPerBatch();
        voteEnabled = value.isVoteEnabled();
        voteAllowCrawledBooks = value.isVoteAllowCrawledBooks();
        voteIpHashKeyId = value.getVoteIpHashKeyId();
        voteMaxTicketsPerRequest = value.getVoteMaxTicketsPerRequest();
        voteMaxVotesPerDay = value.getVoteMaxVotesPerDay();
        voteMaxTicketsPerDay = value.getVoteMaxTicketsPerDay();
        voteMaxTicketsPerBookPerSeason = value.getVoteMaxTicketsPerBookPerSeason();
        voteMaxLotsPerSpend = value.getVoteMaxLotsPerSpend();
        questEnabled = value.isQuestEnabled();
        questHeartbeatIntervalSeconds = value.getQuestHeartbeatIntervalSeconds();
        questHeartbeatMaxMinutesPerDay = value.getQuestHeartbeatMaxMinutesPerDay();
        realmEnabled = value.isRealmEnabled();
        realmChangeCooldownHours = value.getRealmChangeCooldownHours();
        seasonEnabled = value.isSeasonEnabled();
        seasonCloseCron = value.getSeasonCloseCron();
        seasonCloseDrainSeconds = value.getSeasonCloseDrainSeconds();
        seasonResumeDelayMs = value.getSeasonResumeDelayMs();
        seasonReviewWindowHours = value.getSeasonReviewWindowHours();
        rewardEnabled = value.isRewardEnabled();
        rewardClaimWindowDays = value.getRewardClaimWindowDays();
        rewardReleaseCron = value.getRewardReleaseCron();
        jobLeaseSeconds = value.getJobLeaseSeconds();
        jobBatchSize = value.getJobBatchSize();
    }

    private int orZero(Integer value) {
        return value == null ? 0 : value;
    }

    private long orZero(Long value) {
        return value == null ? 0L : value;
    }
}
