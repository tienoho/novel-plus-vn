package com.java2nb.novel.service.gamification.config;

import com.java2nb.novel.mapper.GamificationRuntimeConfigMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GamificationConfigProviderTest {

    @Test
    void keepsLastKnownGoodForReadsButFailsClosedForStaleWrites() {
        AtomicReference<GamificationConfigSnapshot> database =
            new AtomicReference<>(GamificationConfigSnapshot.bootstrapDisabled());
        MutableClock clock = new MutableClock(Instant.parse("2026-08-10T00:00:00Z"));
        CachedGamificationConfigProvider provider = new CachedGamificationConfigProvider(
            database::get, clock, Duration.ofSeconds(60), false);

        provider.refresh();
        database.set(null);
        clock.advance(Duration.ofSeconds(61));

        assertThat(provider.current()).isEqualTo(GamificationConfigSnapshot.bootstrapDisabled());
        assertThatThrownBy(provider::currentForWrite)
            .isInstanceOf(GamificationConfigUnavailableException.class)
            .hasMessageContaining("quá hạn");
    }

    @Test
    void emergencyDisableWinsWithoutMutatingCachedSnapshot() {
        GamificationConfigSnapshot active = GamificationConfigSnapshot.bootstrapDisabled()
            .toBuilder().eventEnabled(true).ticketEnabled(true).build();
        CachedGamificationConfigProvider provider = new CachedGamificationConfigProvider(
            () -> active, Clock.systemUTC(), Duration.ofSeconds(60), true);

        provider.refresh();

        assertThat(provider.currentForWrite().anyWriteEnabled()).isFalse();
        assertThat(active.anyWriteEnabled()).isTrue();
    }

    @Test
    void shadowStartsFromEnvironmentWhenDatabaseIsUnavailable() {
        GamificationRuntimeConfigMapper mapper = mock(GamificationRuntimeConfigMapper.class);
        when(mapper.selectActive()).thenThrow(new IllegalStateException("database unavailable"));
        GamificationEnvironmentProperties environment = new GamificationEnvironmentProperties();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        DatabaseBackedGamificationConfigProvider provider =
            new DatabaseBackedGamificationConfigProvider(mapper, environment,
                bootstrap(GamificationConfigSource.DB_SHADOW), new GamificationConfigValidator(),
                registry, Clock.systemUTC());

        provider.initialize();

        assertThat(provider.current()).isEqualTo(environment.toSnapshot("v1"));
        assertThat(registry.get("gamification.config.refresh.failures").counter().count())
            .isEqualTo(1.0);
        assertThat(registry.get("gamification.config.shadow.diff.keys").gauge().value())
            .isEqualTo(-1.0);
    }

    @Test
    void databaseSourceStillFailsClosedWhenDatabaseIsUnavailable() {
        GamificationRuntimeConfigMapper mapper = mock(GamificationRuntimeConfigMapper.class);
        when(mapper.selectActive()).thenThrow(new IllegalStateException("database unavailable"));
        DatabaseBackedGamificationConfigProvider provider =
            new DatabaseBackedGamificationConfigProvider(mapper,
                new GamificationEnvironmentProperties(), bootstrap(GamificationConfigSource.DB),
                new GamificationConfigValidator(), new SimpleMeterRegistry(), Clock.systemUTC());

        provider.initialize();

        assertThatThrownBy(provider::current)
            .isInstanceOf(GamificationConfigUnavailableException.class)
            .hasMessageContaining("Chưa có snapshot");
    }

    private GamificationBootstrapProperties bootstrap(GamificationConfigSource source) {
        return new GamificationBootstrapProperties() {
            @Override
            public GamificationConfigSource resolveSource() {
                return source;
            }

            @Override
            public boolean isReady(String requiredKeyId) {
                return false;
            }
        };
    }

    private static final class MutableClock extends Clock {
        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
