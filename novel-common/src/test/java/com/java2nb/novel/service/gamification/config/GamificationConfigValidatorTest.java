package com.java2nb.novel.service.gamification.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GamificationConfigValidatorTest {

    private final GamificationConfigValidator validator = new GamificationConfigValidator();

    @Test
    void bootstrapSnapshotIsValidAndFailClosed() {
        GamificationConfigSnapshot snapshot = GamificationConfigSnapshot.bootstrapDisabled();

        assertThat(validator.validate(snapshot, "v1", false)).isEmpty();
        assertThat(snapshot.anyWriteEnabled()).isFalse();
        assertThat(snapshot.getPolicyVersion()).isEqualTo("v1");
        assertThat(snapshot.getRuntimeRevision()).isEqualTo(1L);
    }

    @Test
    void enabledVoteRequiresTicketAndMountedHashKey() {
        GamificationConfigSnapshot voteWithoutTicket = GamificationConfigSnapshot.bootstrapDisabled()
            .toBuilder().voteEnabled(true).voteIpHashKeyId("v2").build();

        assertThat(validator.validate(voteWithoutTicket, "v2", true))
            .contains("vote.ticket.required");

        GamificationConfigSnapshot missingSecret = voteWithoutTicket.toBuilder()
            .ticketEnabled(true).build();
        assertThat(validator.validate(missingSecret, "v1", false))
            .contains("vote.hashSecret.required");
    }

    @Test
    void rejectsContradictoryThresholdsAndCron() {
        GamificationConfigSnapshot invalid = GamificationConfigSnapshot.bootstrapDisabled().toBuilder()
            .voteMaxTicketsPerRequest(10)
            .voteMaxTicketsPerDay(5)
            .voteMaxTicketsPerBookPerSeason(3)
            .ticketExpiryCron("khong-phai-cron")
            .build();

        assertThat(validator.validate(invalid, "v1", false))
            .contains("vote.dailyTickets.belowRequest", "vote.bookTickets.belowRequest",
                "ticket.expiryCron.invalid");
    }

    @Test
    void rejectsValuesThatCanExhaustRuntimeResources() {
        GamificationConfigSnapshot invalid = GamificationConfigSnapshot.bootstrapDisabled().toBuilder()
            .eventDrainBatchSize(10_000)
            .eventDrainDelayMs(100)
            .ticketExpiryCron("* * * * * *")
            .questHeartbeatIntervalSeconds(9)
            .questHeartbeatMaxMinutesPerDay(1_441)
            .jobBatchSize(100_000)
            .voteIpHashKeyId("key id contains spaces")
            .build();

        assertThat(validator.validate(invalid, "v1", false))
            .contains("event.drainThroughput.invalid", "ticket.expiryCron.tooFrequent",
                "quest.heartbeatInterval.invalid", "quest.heartbeatDaily.invalid",
                "job.batch.invalid", "vote.hashKeyId.invalid");
        assertThat(validator.constraints()).containsKeys(
            "eventDrainBatchSize", "questHeartbeatMaxMinutesPerDay", "jobBatchSize");
    }

    @Test
    void classifiesDiffByStrictestEffectiveBoundaryAndRisk() {
        GamificationConfigSnapshot active = GamificationConfigSnapshot.bootstrapDisabled();
        GamificationConfigDiff operational = GamificationConfigDiff.between(active,
            active.toBuilder().eventDrainBatchSize(300).build());
        GamificationConfigDiff nextDay = GamificationConfigDiff.between(active,
            active.toBuilder().questHeartbeatMaxMinutesPerDay(240).build());
        GamificationConfigDiff nextSeason = GamificationConfigDiff.between(active,
            active.toBuilder().ticketEnabled(true).voteMaxTicketsPerDay(60).build());

        assertThat(operational.activationClass()).isEqualTo(GamificationActivationClass.IMMEDIATE);
        assertThat(operational.highRisk()).isFalse();
        assertThat(nextDay.activationClass()).isEqualTo(GamificationActivationClass.NEXT_DAY);
        assertThat(nextDay.highRisk()).isTrue();
        assertThat(nextSeason.activationClass()).isEqualTo(GamificationActivationClass.NEXT_SEASON);
        assertThat(nextSeason.highRisk()).isTrue();
    }

    @Test
    void disablingFeaturesIsImmediateAndDoesNotRequireSecondActor() {
        GamificationConfigSnapshot enabled = GamificationConfigSnapshot.bootstrapDisabled().toBuilder()
            .eventEnabled(true).ticketEnabled(true).build();
        GamificationConfigSnapshot disabled = enabled.toBuilder()
            .eventEnabled(false).ticketEnabled(false).build();

        GamificationConfigDiff diff = GamificationConfigDiff.between(enabled, disabled);

        assertThat(diff.activationClass()).isEqualTo(GamificationActivationClass.IMMEDIATE);
        assertThat(diff.highRisk()).isFalse();
    }
}
