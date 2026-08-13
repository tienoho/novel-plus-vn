package com.java2nb.novel.service.gamification.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GamificationEnvironmentPropertiesTest {

    @Test
    void convertsLegacyEnvironmentValuesIntoOneSnapshot() {
        GamificationEnvironmentProperties properties = new GamificationEnvironmentProperties();
        properties.getEvent().setEnabled(true);
        properties.getEvent().setDrainBatchSize(321);

        GamificationConfigSnapshot snapshot = properties.toSnapshot("secret-v2");

        assertThat(snapshot.isEventEnabled()).isTrue();
        assertThat(snapshot.getEventDrainBatchSize()).isEqualTo(321);
        assertThat(snapshot.getVoteIpHashKeyId()).isEqualTo("secret-v2");
    }

    @Test
    void rejectsLegacyFlagsThatAreLockedByPolicyV1() {
        GamificationEnvironmentProperties properties = new GamificationEnvironmentProperties();
        properties.getTicket().setGrantOnTopUpEnabled(true);

        assertThatThrownBy(() -> properties.toSnapshot("v1"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("TICKET_GRANT_ON_TOPUP");
    }
}
