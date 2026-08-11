package com.java2nb.novel.service.gamification.config;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record GamificationConfigDiff(Set<String> changedKeys,
                                     GamificationActivationClass activationClass,
                                     boolean highRisk) {

    public GamificationConfigDiff {
        changedKeys = Set.copyOf(changedKeys);
    }

    public static GamificationConfigDiff between(GamificationConfigSnapshot oldValue,
                                                  GamificationConfigSnapshot newValue) {
        Objects.requireNonNull(oldValue, "oldValue");
        Objects.requireNonNull(newValue, "newValue");
        Set<String> keys = changedKeys(oldValue, newValue);
        if (keys.isEmpty()) {
            return new GamificationConfigDiff(keys, GamificationActivationClass.IMMEDIATE, false);
        }
        if (onlyFeatureDisables(oldValue, newValue, keys)) {
            return new GamificationConfigDiff(keys, GamificationActivationClass.IMMEDIATE, false);
        }
        GamificationActivationClass activation = GamificationActivationClass.IMMEDIATE;
        boolean highRisk = false;
        for (String key : keys) {
            if (NEXT_SEASON.contains(key)) {
                activation = GamificationActivationClass.NEXT_SEASON;
                highRisk = true;
            } else if (NEXT_DAY.contains(key)
                && activation != GamificationActivationClass.NEXT_SEASON) {
                activation = GamificationActivationClass.NEXT_DAY;
                highRisk = true;
            } else if (HIGH_RISK_IMMEDIATE.contains(key)) {
                highRisk = true;
            }
        }
        return new GamificationConfigDiff(keys, activation, highRisk);
    }

    private static final Set<String> NEXT_SEASON = Set.of(
        "policyVersion", "zoneId", "ticketLotValidityDays", "ticketMaxGrantPerBatch",
        "voteEnabled", "voteAllowCrawledBooks", "voteIpHashKeyId",
        "voteMaxTicketsPerRequest", "voteMaxVotesPerDay", "voteMaxTicketsPerDay",
        "voteMaxTicketsPerBookPerSeason", "voteMaxLotsPerSpend", "seasonEnabled",
        "seasonReviewWindowHours", "rewardEnabled", "rewardClaimWindowDays");
    private static final Set<String> NEXT_DAY = Set.of(
        "questEnabled", "questHeartbeatIntervalSeconds", "questHeartbeatMaxMinutesPerDay");
    private static final Set<String> HIGH_RISK_IMMEDIATE = Set.of(
        "eventEnabled", "ticketEnabled", "realmEnabled", "realmChangeCooldownHours");
    private static final Set<String> FEATURE_FLAGS = Set.of(
        "eventEnabled", "ticketEnabled", "voteEnabled", "questEnabled", "realmEnabled",
        "seasonEnabled", "rewardEnabled");

    private static boolean onlyFeatureDisables(GamificationConfigSnapshot oldValue,
                                               GamificationConfigSnapshot newValue,
                                               Set<String> keys) {
        if (!FEATURE_FLAGS.containsAll(keys)) {
            return false;
        }
        return (!keys.contains("eventEnabled") || oldValue.isEventEnabled() && !newValue.isEventEnabled())
            && (!keys.contains("ticketEnabled") || oldValue.isTicketEnabled() && !newValue.isTicketEnabled())
            && (!keys.contains("voteEnabled") || oldValue.isVoteEnabled() && !newValue.isVoteEnabled())
            && (!keys.contains("questEnabled") || oldValue.isQuestEnabled() && !newValue.isQuestEnabled())
            && (!keys.contains("realmEnabled") || oldValue.isRealmEnabled() && !newValue.isRealmEnabled())
            && (!keys.contains("seasonEnabled") || oldValue.isSeasonEnabled() && !newValue.isSeasonEnabled())
            && (!keys.contains("rewardEnabled") || oldValue.isRewardEnabled() && !newValue.isRewardEnabled());
    }

    private static Set<String> changedKeys(GamificationConfigSnapshot a,
                                           GamificationConfigSnapshot b) {
        Set<String> keys = new LinkedHashSet<>();
        changed(keys, "policyVersion", a.getPolicyVersion(), b.getPolicyVersion());
        changed(keys, "zoneId", a.getZoneId(), b.getZoneId());
        changed(keys, "eventEnabled", a.isEventEnabled(), b.isEventEnabled());
        changed(keys, "eventDrainBatchSize", a.getEventDrainBatchSize(), b.getEventDrainBatchSize());
        changed(keys, "eventDrainDelayMs", a.getEventDrainDelayMs(), b.getEventDrainDelayMs());
        changed(keys, "eventMaxAttempt", a.getEventMaxAttempt(), b.getEventMaxAttempt());
        changed(keys, "ticketEnabled", a.isTicketEnabled(), b.isTicketEnabled());
        changed(keys, "ticketLotValidityDays", a.getTicketLotValidityDays(), b.getTicketLotValidityDays());
        changed(keys, "ticketExpiryCron", a.getTicketExpiryCron(), b.getTicketExpiryCron());
        changed(keys, "ticketExpiryBatchSize", a.getTicketExpiryBatchSize(), b.getTicketExpiryBatchSize());
        changed(keys, "ticketMaxGrantPerBatch", a.getTicketMaxGrantPerBatch(), b.getTicketMaxGrantPerBatch());
        changed(keys, "voteEnabled", a.isVoteEnabled(), b.isVoteEnabled());
        changed(keys, "voteAllowCrawledBooks", a.isVoteAllowCrawledBooks(), b.isVoteAllowCrawledBooks());
        changed(keys, "voteIpHashKeyId", a.getVoteIpHashKeyId(), b.getVoteIpHashKeyId());
        changed(keys, "voteMaxTicketsPerRequest", a.getVoteMaxTicketsPerRequest(), b.getVoteMaxTicketsPerRequest());
        changed(keys, "voteMaxVotesPerDay", a.getVoteMaxVotesPerDay(), b.getVoteMaxVotesPerDay());
        changed(keys, "voteMaxTicketsPerDay", a.getVoteMaxTicketsPerDay(), b.getVoteMaxTicketsPerDay());
        changed(keys, "voteMaxTicketsPerBookPerSeason", a.getVoteMaxTicketsPerBookPerSeason(), b.getVoteMaxTicketsPerBookPerSeason());
        changed(keys, "voteMaxLotsPerSpend", a.getVoteMaxLotsPerSpend(), b.getVoteMaxLotsPerSpend());
        changed(keys, "questEnabled", a.isQuestEnabled(), b.isQuestEnabled());
        changed(keys, "questHeartbeatIntervalSeconds", a.getQuestHeartbeatIntervalSeconds(), b.getQuestHeartbeatIntervalSeconds());
        changed(keys, "questHeartbeatMaxMinutesPerDay", a.getQuestHeartbeatMaxMinutesPerDay(), b.getQuestHeartbeatMaxMinutesPerDay());
        changed(keys, "realmEnabled", a.isRealmEnabled(), b.isRealmEnabled());
        changed(keys, "realmChangeCooldownHours", a.getRealmChangeCooldownHours(), b.getRealmChangeCooldownHours());
        changed(keys, "seasonEnabled", a.isSeasonEnabled(), b.isSeasonEnabled());
        changed(keys, "seasonCloseCron", a.getSeasonCloseCron(), b.getSeasonCloseCron());
        changed(keys, "seasonCloseDrainSeconds", a.getSeasonCloseDrainSeconds(), b.getSeasonCloseDrainSeconds());
        changed(keys, "seasonResumeDelayMs", a.getSeasonResumeDelayMs(), b.getSeasonResumeDelayMs());
        changed(keys, "seasonReviewWindowHours", a.getSeasonReviewWindowHours(), b.getSeasonReviewWindowHours());
        changed(keys, "rewardEnabled", a.isRewardEnabled(), b.isRewardEnabled());
        changed(keys, "rewardClaimWindowDays", a.getRewardClaimWindowDays(), b.getRewardClaimWindowDays());
        changed(keys, "rewardReleaseCron", a.getRewardReleaseCron(), b.getRewardReleaseCron());
        changed(keys, "jobLeaseSeconds", a.getJobLeaseSeconds(), b.getJobLeaseSeconds());
        changed(keys, "jobBatchSize", a.getJobBatchSize(), b.getJobBatchSize());
        return keys;
    }

    private static void changed(Set<String> keys, String key, Object a, Object b) {
        if (!Objects.equals(a, b)) {
            keys.add(key);
        }
    }
}
