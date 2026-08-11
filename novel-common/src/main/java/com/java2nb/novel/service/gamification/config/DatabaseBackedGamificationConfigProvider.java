package com.java2nb.novel.service.gamification.config;

import com.java2nb.novel.mapper.GamificationRuntimeConfigMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Primary
@Component
public class DatabaseBackedGamificationConfigProvider implements GamificationConfigProvider {

    private final GamificationRuntimeConfigMapper mapper;
    private final GamificationEnvironmentProperties environment;
    private final GamificationBootstrapProperties bootstrap;
    private final GamificationConfigValidator validator;
    private final CachedGamificationConfigProvider cache;
    private final Counter refreshFailures;
    private final AtomicLong shadowDiffKeys = new AtomicLong();
    private final AtomicLong nextRefreshAtMs = new AtomicLong();
    private final Clock clock;

    @Autowired
    public DatabaseBackedGamificationConfigProvider(GamificationRuntimeConfigMapper mapper,
                                                     GamificationEnvironmentProperties environment,
                                                     GamificationBootstrapProperties bootstrap,
                                                     GamificationConfigValidator validator,
                                                     MeterRegistry registry) {
        this(mapper, environment, bootstrap, validator, registry, Clock.systemUTC());
    }

    DatabaseBackedGamificationConfigProvider(GamificationRuntimeConfigMapper mapper,
                                              GamificationEnvironmentProperties environment,
                                              GamificationBootstrapProperties bootstrap,
                                              GamificationConfigValidator validator,
                                              MeterRegistry registry, Clock clock) {
        this.mapper = mapper;
        this.environment = environment;
        this.bootstrap = bootstrap;
        this.validator = validator;
        this.clock = clock;
        this.cache = new CachedGamificationConfigProvider(this::loadSelectedSnapshot, clock,
            Duration.ofMillis(positive(bootstrap.getMaxStaleMs(), "GAMIFICATION_CONFIG_MAX_STALE_MS")),
            bootstrap.isForceDisable());
        this.refreshFailures = Counter.builder("gamification.config.refresh.failures")
            .description("Số lần refresh cấu hình gamification thất bại")
            .register(registry);
        Gauge.builder("gamification.config.revision", cache, CachedGamificationConfigProvider::currentRevision)
            .description("Revision cấu hình gamification đang dùng").register(registry);
        Gauge.builder("gamification.config.snapshot.age.seconds", cache,
                value -> value.snapshotAge().toMillis() / 1_000.0)
            .description("Tuổi snapshot cấu hình gamification").register(registry);
        Gauge.builder("gamification.config.max.stale.seconds", bootstrap,
                value -> value.getMaxStaleMs() / 1_000.0)
            .description("Tuổi snapshot tối đa trước khi khóa đường ghi")
            .register(registry);
        Gauge.builder("gamification.config.shadow.diff.keys", shadowDiffKeys, AtomicLong::get)
            .description("Số key khác nhau giữa ENV và DB trong shadow mode").register(registry);
        Gauge.builder("gamification.config.source", () -> 1)
            .tag("source", bootstrap.resolveSource().name())
            .description("Nguồn snapshot cấu hình gamification đang được chọn")
            .register(registry);
    }

    @PostConstruct
    void initialize() {
        refreshSafely();
    }

    @Override
    public GamificationConfigSnapshot current() {
        refreshIfDue();
        return cache.current();
    }

    @Override
    public GamificationConfigSnapshot currentForWrite() {
        refreshIfDue();
        return cache.currentForWrite();
    }

    @Override
    @Scheduled(fixedDelayString = "${GAMIFICATION_CONFIG_REFRESH_MS:2000}")
    public void refresh() {
        refreshSafely();
    }

    private void refreshIfDue() {
        long now = clock.millis();
        if (now >= nextRefreshAtMs.get()) {
            refreshSafely();
        }
    }

    private synchronized void refreshSafely() {
        long now = clock.millis();
        if (now < nextRefreshAtMs.get()) {
            return;
        }
        nextRefreshAtMs.set(now + positive(bootstrap.getRefreshMs(), "GAMIFICATION_CONFIG_REFRESH_MS"));
        try {
            cache.refresh();
        } catch (RuntimeException exception) {
            refreshFailures.increment();
            log.error("Không thể refresh cấu hình gamification từ {}", bootstrap.resolveSource(), exception);
        }
    }

    private GamificationConfigSnapshot loadSelectedSnapshot() {
        GamificationConfigSource source = bootstrap.resolveSource();
        if (source == GamificationConfigSource.DB) {
            return loadDatabaseSnapshot();
        }
        GamificationConfigSnapshot env = loadEnvironmentSnapshot();
        if (source == GamificationConfigSource.DB_SHADOW) {
            compareShadowSafely(env);
        } else {
            shadowDiffKeys.set(0);
        }
        return env;
    }

    private void compareShadowSafely(GamificationConfigSnapshot environmentSnapshot) {
        try {
            compareShadow(environmentSnapshot, loadDatabaseSnapshot());
        } catch (RuntimeException exception) {
            shadowDiffKeys.set(-1);
            refreshFailures.increment();
            log.error("Không thể so sánh cấu hình gamification DB_SHADOW; tiếp tục dùng ENV", exception);
        }
    }

    private GamificationConfigSnapshot loadEnvironmentSnapshot() {
        GamificationConfigSnapshot value = environment.toSnapshot(bootstrap.getVoteIpHashKeyId());
        validate(value);
        return value;
    }

    private GamificationConfigSnapshot loadDatabaseSnapshot() {
        GamificationRuntimeConfigRow row = mapper.selectActive();
        if (row == null) {
            throw new GamificationConfigUnavailableException("Database chưa có ACTIVE gamification config");
        }
        GamificationConfigSnapshot value = row.getSnapshot();
        validate(value);
        return value;
    }

    private void compareShadow(GamificationConfigSnapshot env, GamificationConfigSnapshot database) {
        GamificationConfigDiff diff = GamificationConfigDiff.between(env, database);
        shadowDiffKeys.set(diff.changedKeys().size());
        if (!diff.changedKeys().isEmpty()) {
            log.warn("DB_SHADOW phát hiện cấu hình gamification lệch ở các key: {}",
                diff.changedKeys());
        }
    }

    private void validate(GamificationConfigSnapshot value) {
        List<String> errors = validator.validate(value, bootstrap.getVoteIpHashKeyId(),
            bootstrap.isReady(value.getVoteIpHashKeyId()));
        if (!errors.isEmpty()) {
            throw new GamificationConfigUnavailableException(
                "Snapshot cấu hình gamification không hợp lệ: " + String.join(", ", errors));
        }
    }

    private long positive(long value, String name) {
        if (value <= 0) {
            throw new IllegalArgumentException(name + " phải lớn hơn 0");
        }
        return value;
    }
}
