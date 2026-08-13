package com.java2nb.novel.service.gamification.config;

import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Component;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class GamificationConfigValidator {

    private static final long MAX_EVENT_DRAIN_PER_SECOND = 2_000L;
    private static final Pattern HASH_KEY_ID = Pattern.compile("[A-Za-z0-9._-]{1,64}");
    private static final Map<String, NumericConstraint> NUMERIC_CONSTRAINTS = constraintsMap();

    public record NumericConstraint(long min, long max) {
    }

    public Map<String, NumericConstraint> constraints() {
        return NUMERIC_CONSTRAINTS;
    }

    public List<String> validate(GamificationConfigSnapshot value, String mountedHashKeyId,
                                 boolean hashSecretReady) {
        List<String> errors = new ArrayList<>();
        if (value == null) {
            return List.of("config.required");
        }
        if (!isPolicyVersion(value.getPolicyVersion())) {
            errors.add("policyVersion.invalid");
        }
        try {
            ZoneId.of(value.getZoneId());
        } catch (DateTimeException | NullPointerException exception) {
            errors.add("zoneId.invalid");
        }
        constrained(value.getEventDrainBatchSize(), "eventDrainBatchSize",
            "event.drainBatch.invalid", errors);
        constrained(value.getEventDrainDelayMs(), "eventDrainDelayMs",
            "event.drainDelay.invalid", errors);
        constrained(value.getEventMaxAttempt(), "eventMaxAttempt",
            "event.maxAttempt.invalid", errors);
        constrained(value.getTicketLotValidityDays(), "ticketLotValidityDays",
            "ticket.lotValidity.invalid", errors);
        cron(value.getTicketExpiryCron(), "ticket.expiryCron.invalid", errors);
        constrained(value.getTicketExpiryBatchSize(), "ticketExpiryBatchSize",
            "ticket.expiryBatch.invalid", errors);
        constrained(value.getTicketMaxGrantPerBatch(), "ticketMaxGrantPerBatch",
            "ticket.maxGrant.invalid", errors);
        constrained(value.getVoteMaxTicketsPerRequest(), "voteMaxTicketsPerRequest",
            "vote.maxRequest.invalid", errors);
        constrained(value.getVoteMaxVotesPerDay(), "voteMaxVotesPerDay",
            "vote.maxVotesDay.invalid", errors);
        constrained(value.getVoteMaxTicketsPerDay(), "voteMaxTicketsPerDay",
            "vote.maxTicketsDay.invalid", errors);
        constrained(value.getVoteMaxTicketsPerBookPerSeason(),
            "voteMaxTicketsPerBookPerSeason", "vote.maxBook.invalid", errors);
        constrained(value.getVoteMaxLotsPerSpend(), "voteMaxLotsPerSpend",
            "vote.maxLots.invalid", errors);
        constrained(value.getQuestHeartbeatIntervalSeconds(), "questHeartbeatIntervalSeconds",
            "quest.heartbeatInterval.invalid", errors);
        constrained(value.getQuestHeartbeatMaxMinutesPerDay(),
            "questHeartbeatMaxMinutesPerDay", "quest.heartbeatDaily.invalid", errors);
        constrained(value.getRealmChangeCooldownHours(), "realmChangeCooldownHours",
            "realm.cooldown.invalid", errors);
        constrained(value.getSeasonCloseDrainSeconds(), "seasonCloseDrainSeconds",
            "season.drain.invalid", errors);
        constrained(value.getSeasonResumeDelayMs(), "seasonResumeDelayMs",
            "season.resumeDelay.invalid", errors);
        constrained(value.getSeasonReviewWindowHours(), "seasonReviewWindowHours",
            "season.reviewWindow.invalid", errors);
        cron(value.getSeasonCloseCron(), "season.closeCron.invalid", errors);
        constrained(value.getRewardClaimWindowDays(), "rewardClaimWindowDays",
            "reward.claimWindow.invalid", errors);
        cron(value.getRewardReleaseCron(), "reward.releaseCron.invalid", errors);
        constrained(value.getJobLeaseSeconds(), "jobLeaseSeconds", "job.lease.invalid", errors);
        constrained(value.getJobBatchSize(), "jobBatchSize", "job.batch.invalid", errors);
        if ((long) value.getEventDrainBatchSize() * 1_000L
            > value.getEventDrainDelayMs() * MAX_EVENT_DRAIN_PER_SECOND) {
            errors.add("event.drainThroughput.invalid");
        }

        if (!HASH_KEY_ID.matcher(value.getVoteIpHashKeyId() == null
            ? "" : value.getVoteIpHashKeyId()).matches()) {
            errors.add("vote.hashKeyId.invalid");
        }

        if (value.getVoteMaxTicketsPerDay() < value.getVoteMaxTicketsPerRequest()) {
            errors.add("vote.dailyTickets.belowRequest");
        }
        if (value.getVoteMaxTicketsPerBookPerSeason() < value.getVoteMaxTicketsPerRequest()) {
            errors.add("vote.bookTickets.belowRequest");
        }
        if (value.isVoteEnabled() && !value.isTicketEnabled()) {
            errors.add("vote.ticket.required");
        }
        if (value.isQuestEnabled() && !value.isEventEnabled()) {
            errors.add("quest.event.required");
        }
        if (value.isRewardEnabled() && !value.isSeasonEnabled()) {
            errors.add("reward.season.required");
        }
        if (value.isVoteEnabled()
            && (!hashSecretReady || !sameKey(value.getVoteIpHashKeyId(), mountedHashKeyId))) {
            errors.add("vote.hashSecret.required");
        }
        return List.copyOf(errors);
    }

    private boolean isPolicyVersion(String value) {
        return value != null && value.matches("[a-z0-9][a-z0-9._-]{0,31}");
    }

    private boolean sameKey(String required, String mounted) {
        return required != null && !required.isBlank() && required.equals(mounted);
    }

    private void cron(String value, String error, List<String> errors) {
        if (value == null || value.length() > 64) {
            errors.add(error);
            return;
        }
        try {
            CronExpression.parse(value);
            String[] fields = value.trim().split("\\s+");
            if (fields.length != 6 || !fields[0].matches("(?:[0-9]|[0-5][0-9])")) {
                errors.add(error.replace(".invalid", ".tooFrequent"));
            }
        } catch (RuntimeException exception) {
            errors.add(error);
        }
    }

    private void constrained(long value, String field, String error, List<String> errors) {
        NumericConstraint constraint = NUMERIC_CONSTRAINTS.get(field);
        if (constraint == null || value < constraint.min() || value > constraint.max()) {
            errors.add(error);
        }
    }

    private static Map<String, NumericConstraint> constraintsMap() {
        Map<String, NumericConstraint> values = new LinkedHashMap<>();
        values.put("eventDrainBatchSize", new NumericConstraint(1, 10_000));
        values.put("eventDrainDelayMs", new NumericConstraint(100, 3_600_000));
        values.put("eventMaxAttempt", new NumericConstraint(1, 100));
        values.put("ticketLotValidityDays", new NumericConstraint(1, 3_650));
        values.put("ticketExpiryBatchSize", new NumericConstraint(1, 10_000));
        values.put("ticketMaxGrantPerBatch", new NumericConstraint(1, 1_000_000));
        values.put("voteMaxTicketsPerRequest", new NumericConstraint(1, 1_000));
        values.put("voteMaxVotesPerDay", new NumericConstraint(1, 10_000));
        values.put("voteMaxTicketsPerDay", new NumericConstraint(1, 100_000));
        values.put("voteMaxTicketsPerBookPerSeason", new NumericConstraint(1, 1_000_000));
        values.put("voteMaxLotsPerSpend", new NumericConstraint(1, 10_000));
        values.put("questHeartbeatIntervalSeconds", new NumericConstraint(10, 3_600));
        values.put("questHeartbeatMaxMinutesPerDay", new NumericConstraint(1, 1_440));
        values.put("realmChangeCooldownHours", new NumericConstraint(1, 8_760));
        values.put("seasonCloseDrainSeconds", new NumericConstraint(0, 86_400));
        values.put("seasonResumeDelayMs", new NumericConstraint(100, 3_600_000));
        values.put("seasonReviewWindowHours", new NumericConstraint(1, 720));
        values.put("rewardClaimWindowDays", new NumericConstraint(1, 365));
        values.put("jobLeaseSeconds", new NumericConstraint(1, 86_400));
        values.put("jobBatchSize", new NumericConstraint(1, 10_000));
        return Map.copyOf(values);
    }
}
