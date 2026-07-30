package com.java2nb.novel.service.gamification;

/** Kết quả claim một phase; thất bại cạnh tranh là kết quả bình thường, không phải exception. */
public record SeasonPhaseResult(Outcome outcome, long seasonId, Long snapshotId, String seasonStatus) {

    public enum Outcome {
        OWNER,
        NOT_OWNER,
        WAITING,
        COMPLETED
    }

    public static SeasonPhaseResult owner(long seasonId, Long snapshotId, String seasonStatus) {
        return new SeasonPhaseResult(Outcome.OWNER, seasonId, snapshotId, seasonStatus);
    }

    public static SeasonPhaseResult notOwner(long seasonId, Long snapshotId, String seasonStatus) {
        return new SeasonPhaseResult(Outcome.NOT_OWNER, seasonId, snapshotId, seasonStatus);
    }

    public static SeasonPhaseResult waiting(long seasonId, Long snapshotId, String seasonStatus) {
        return new SeasonPhaseResult(Outcome.WAITING, seasonId, snapshotId, seasonStatus);
    }

    public static SeasonPhaseResult completed(long seasonId, Long snapshotId, String seasonStatus) {
        return new SeasonPhaseResult(Outcome.COMPLETED, seasonId, snapshotId, seasonStatus);
    }
}
