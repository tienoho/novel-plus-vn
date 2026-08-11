package com.java2nb.novel.service.gamification.config;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

public class CachedGamificationConfigProvider implements GamificationConfigProvider {

    private final GamificationConfigSnapshotSource source;
    private final Clock clock;
    private final Duration maxStale;
    private final boolean forceDisable;
    private final AtomicReference<CachedSnapshot> lastKnownGood = new AtomicReference<>();

    public CachedGamificationConfigProvider(GamificationConfigSnapshotSource source, Clock clock,
                                             Duration maxStale, boolean forceDisable) {
        this.source = Objects.requireNonNull(source, "source");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.maxStale = Objects.requireNonNull(maxStale, "maxStale");
        if (maxStale.isNegative() || maxStale.isZero()) {
            throw new IllegalArgumentException("Thời gian stale tối đa phải lớn hơn 0");
        }
        this.forceDisable = forceDisable;
    }

    @Override
    public GamificationConfigSnapshot current() {
        CachedSnapshot cached = requireCached();
        return forceDisable ? disabled(cached.snapshot()) : cached.snapshot();
    }

    @Override
    public GamificationConfigSnapshot currentForWrite() {
        CachedSnapshot cached = requireCached();
        if (Duration.between(cached.loadedAt(), clock.instant()).compareTo(maxStale) > 0) {
            throw new GamificationConfigUnavailableException(
                "Snapshot cấu hình gamification đã quá hạn; đường ghi bị khóa an toàn");
        }
        return forceDisable ? disabled(cached.snapshot()) : cached.snapshot();
    }

    @Override
    public void refresh() {
        GamificationConfigSnapshot loaded = source.loadActive();
        if (loaded != null) {
            lastKnownGood.set(new CachedSnapshot(loaded, clock.instant()));
        }
    }

    public long currentRevision() {
        CachedSnapshot cached = lastKnownGood.get();
        return cached == null ? 0 : cached.snapshot().getRuntimeRevision();
    }

    public Duration snapshotAge() {
        CachedSnapshot cached = lastKnownGood.get();
        return cached == null ? Duration.ofMillis(Long.MAX_VALUE)
            : Duration.between(cached.loadedAt(), clock.instant());
    }

    private CachedSnapshot requireCached() {
        CachedSnapshot cached = lastKnownGood.get();
        if (cached == null) {
            throw new GamificationConfigUnavailableException(
                "Chưa có snapshot cấu hình gamification hợp lệ");
        }
        return cached;
    }

    private GamificationConfigSnapshot disabled(GamificationConfigSnapshot snapshot) {
        return snapshot.toBuilder()
            .eventEnabled(false)
            .ticketEnabled(false)
            .voteEnabled(false)
            .questEnabled(false)
            .realmEnabled(false)
            .seasonEnabled(false)
            .rewardEnabled(false)
            .build();
    }

    private record CachedSnapshot(GamificationConfigSnapshot snapshot, Instant loadedAt) {
    }
}
