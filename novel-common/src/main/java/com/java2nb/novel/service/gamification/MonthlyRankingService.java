package com.java2nb.novel.service.gamification;

import java.util.Date;
import java.time.ZoneId;
import java.util.List;

/** Điều phối state machine kỳ và snapshot xếp hạng Ngọn Đuốc. */
public interface MonthlyRankingService {

    /** Tạo lười kỳ thường chứa thời điểm {@code at}; mọi biên tháng được tính ở Java. */
    MonthlySeasonRow ensureRegularSeason(Date at, ZoneId zoneId, String policyVersion,
                                         long runtimeConfigRevision);

    /**
     * Tạo kỳ đặc biệt (lễ hội, kỷ niệm...) do admin định nghĩa thủ công. {@code seasonType}
     * khác 'REGULAR' để không bị bộ lịch tự động ({@link #ensureRegularSeason}) ghi đè hay
     * gộp vào kỳ thường — mọi truy vấn chọn kỳ đang hoạt động chỉ xét {@code season_type = 'REGULAR'}.
     */
    MonthlySeasonRow createSpecialSeason(String periodCode, String seasonType, Date startAt,
                                         Date endAt, Date voteCutoffAt, ZoneId zoneId,
                                         String policyVersion, long runtimeConfigRevision);

    List<MonthlySeasonRow> listSeasonsReadyToClose(Date at, int limit);

    List<MonthlySeasonRow> listClosingSeasons(int limit);

    /** Các kỳ đang mở tại thời điểm đọc để người dùng chọn rõ {@code seasonId}. */
    List<MonthlySeasonRow> listOpenSeasons(Date at);

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

    MonthlyRankingPage getRanking(Long seasonId, String periodCode, int page, int pageSize, Date at);

    SeasonPhaseResult pauseSnapshot(long seasonId, long operatorId, String reason, Date at);

    SeasonPhaseResult retrySnapshot(long seasonId, String ownerInstance, Date runAt,
                                    int closeDrainSeconds, int leaseSeconds, int batchSize);

    List<MonthlyRankDriftRow> reconcile(long seasonId);

    SeasonPhaseResult finalizeSeason(long seasonId, long operatorId, Date finalizedAt);
}
