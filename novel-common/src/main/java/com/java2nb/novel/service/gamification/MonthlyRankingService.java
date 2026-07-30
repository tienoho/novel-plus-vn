package com.java2nb.novel.service.gamification;

import java.util.Date;
import java.time.ZoneId;
import java.util.List;

/** Điều phối state machine kỳ và snapshot xếp hạng Ngọn Đuốc. */
public interface MonthlyRankingService {

    /** Tạo lười kỳ thường chứa thời điểm {@code at}; mọi biên tháng được tính ở Java. */
    MonthlySeasonRow ensureRegularSeason(Date at, ZoneId zoneId, String policyVersion);

    List<MonthlySeasonRow> listSeasonsReadyToClose(Date at, int limit);

    List<MonthlySeasonRow> listClosingSeasons(int limit);

    /** Claim duy nhất chuyển kỳ từ OPEN sang CLOSING. */
    SeasonPhaseResult closeSeason(long seasonId, Date closingAt);

    /** Claim đóng kỳ và build ngay khi caller sở hữu phase; caller thua không tranh snapshot. */
    SeasonPhaseResult closeSeason(long seasonId, Date closingAt, String ownerInstance,
                                  int closeDrainSeconds, int leaseSeconds, int batchSize);

    /**
     * Chụp snapshot theo batch có lease/checkpoint, niêm phong hash và chuyển kỳ sang REVIEW.
     * Gọi lại sau lỗi sẽ tiếp tục từ checkpoint của batch cuối đã commit.
     */
    SeasonPhaseResult buildSnapshot(long seasonId, String ownerInstance, Date runAt,
                                    int closeDrainSeconds, int leaseSeconds, int batchSize);

    MonthlyRankingPage getRanking(String periodCode, int page, int pageSize, Date at);

    SeasonPhaseResult pauseSnapshot(long seasonId, long operatorId, String reason, Date at);

    SeasonPhaseResult retrySnapshot(long seasonId, String ownerInstance, Date runAt,
                                    int closeDrainSeconds, int leaseSeconds, int batchSize);

    List<MonthlyRankDriftRow> reconcile(long seasonId);

    SeasonPhaseResult finalizeSeason(long seasonId, long operatorId, Date finalizedAt);
}
