package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.MonthlyRankingMapper;
import com.java2nb.novel.service.gamification.MonthlyRankingService;
import com.java2nb.novel.service.gamification.MonthlyRankingPage;
import com.java2nb.novel.service.gamification.MonthlyRankRow;
import com.java2nb.novel.service.gamification.MonthlyRankDriftRow;
import com.java2nb.novel.service.gamification.MonthlyRankSnapshotRow;
import com.java2nb.novel.service.gamification.MonthlySeasonRow;
import com.java2nb.novel.service.gamification.SeasonPhaseResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.Clock;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;

@Service
public class MonthlyRankingServiceImpl implements MonthlyRankingService {

    private static final String SNAPSHOT_JOB = "SEASON_SNAPSHOT";
    private static final String SEASON_SCOPE = "SEASON";

    private final MonthlyRankingMapper monthlyRankingMapper;
    private final MonthlyRankingBatchWriter batchWriter;
    private final Clock clock;

    private static final int MAX_PAGE_SIZE = 100;
    private static final int MAX_SEASON_BATCH_SIZE = 500;

    @Autowired
    public MonthlyRankingServiceImpl(MonthlyRankingMapper monthlyRankingMapper,
                                     MonthlyRankingBatchWriter batchWriter) {
        this(monthlyRankingMapper, batchWriter, Clock.systemUTC());
    }

    public MonthlyRankingServiceImpl(MonthlyRankingMapper monthlyRankingMapper,
                                     MonthlyRankingBatchWriter batchWriter, Clock clock) {
        this.monthlyRankingMapper = monthlyRankingMapper;
        this.batchWriter = batchWriter;
        this.clock = clock;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public MonthlySeasonRow ensureRegularSeason(Date at, ZoneId zoneId, String policyVersion,
                                                long runtimeConfigRevision) {
        Objects.requireNonNull(at, "Thiếu thời điểm tạo kỳ xếp hạng");
        Objects.requireNonNull(zoneId, "Thiếu múi giờ tạo kỳ xếp hạng");
        if (policyVersion == null || policyVersion.isBlank() || runtimeConfigRevision <= 0) {
            throw new IllegalArgumentException("Thiếu phiên bản chính sách của kỳ xếp hạng");
        }
        ZonedDateTime local = at.toInstant().atZone(zoneId);
        YearMonth month = YearMonth.from(local);
        ZonedDateTime start = month.atDay(1).atStartOfDay(zoneId);
        ZonedDateTime end = month.plusMonths(1).atDay(1).atStartOfDay(zoneId);
        String periodCode = month.toString();
        monthlyRankingMapper.insertRegularSeasonIgnore(periodCode, zoneId.getId(),
            Date.from(start.toInstant()), Date.from(end.toInstant()), Date.from(end.toInstant()),
            policyVersion, runtimeConfigRevision);
        MonthlySeasonRow season = monthlyRankingMapper.selectSeasonByPeriod(periodCode);
        if (season == null) {
            throw new IllegalStateException("Không thể tạo hoặc đọc kỳ xếp hạng tháng hiện tại");
        }
        return season;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public MonthlySeasonRow createSpecialSeason(String periodCode, String seasonType, Date startAt,
                                                Date endAt, Date voteCutoffAt, ZoneId zoneId,
                                                String policyVersion, long runtimeConfigRevision) {
        Objects.requireNonNull(zoneId, "Thiếu múi giờ kỳ đặc biệt");
        if (periodCode == null || !periodCode.matches("[a-z0-9][a-z0-9-]{0,31}")) {
            throw new IllegalArgumentException(
                "Slug kỳ đặc biệt phải dài từ 1 đến 32 ký tự và chỉ gồm chữ thường, số, dấu gạch ngang");
        }
        if (seasonType == null || !seasonType.matches("[A-Z][A-Z0-9_]{1,23}")
            || "REGULAR".equals(seasonType)) {
            throw new IllegalArgumentException(
                "Loại kỳ đặc biệt phải viết hoa, tối đa 24 ký tự và khác 'REGULAR'");
        }
        if (policyVersion == null || policyVersion.isBlank() || runtimeConfigRevision <= 0) {
            throw new IllegalArgumentException("Thiếu phiên bản chính sách của kỳ đặc biệt");
        }
        if (startAt == null || endAt == null || voteCutoffAt == null || !endAt.after(startAt)
            || !voteCutoffAt.after(startAt) || voteCutoffAt.after(endAt)) {
            throw new IllegalArgumentException("Khung thời gian kỳ đặc biệt không hợp lệ");
        }
        monthlyRankingMapper.insertSpecialSeasonIgnore(periodCode, seasonType, zoneId.getId(),
            startAt, endAt, voteCutoffAt, policyVersion, runtimeConfigRevision);
        MonthlySeasonRow season = monthlyRankingMapper.selectSeasonByPeriod(periodCode);
        if (season == null) {
            throw new IllegalStateException("Không thể tạo hoặc đọc kỳ đặc biệt vừa tạo");
        }
        if (!seasonType.equals(season.getSeasonType())) {
            throw new IllegalStateException("Mã kỳ đặc biệt đã tồn tại với loại kỳ khác");
        }
        return season;
    }

    @Override
    public List<MonthlySeasonRow> listSeasonsReadyToClose(Date at, int limit) {
        Objects.requireNonNull(at, "Thiếu thời điểm tìm kỳ cần đóng");
        return monthlyRankingMapper.selectSeasonsReadyToClose(at, safeSeasonLimit(limit));
    }

    @Override
    public List<MonthlySeasonRow> listClosingSeasons(int limit) {
        return monthlyRankingMapper.selectClosingSeasons(safeSeasonLimit(limit));
    }

    @Override
    public List<MonthlySeasonRow> listOpenSeasons(Date at) {
        Objects.requireNonNull(at, "Thiếu thời điểm liệt kê kỳ đang mở");
        return monthlyRankingMapper.selectOpenSeasons(at);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SeasonPhaseResult closeSeason(long seasonId, Date closingAt) {
        requireSeasonId(seasonId);
        Objects.requireNonNull(closingAt, "Thiếu thời điểm đóng kỳ Ngọn Đuốc");
        MonthlySeasonRow season = requireSeason(seasonId);
        if (!"OPEN".equals(season.getStatus())) {
            return SeasonPhaseResult.notOwner(seasonId, season.getSnapshotId(), season.getStatus());
        }
        int claimed = monthlyRankingMapper.claimSeasonClosing(
            seasonId, season.getVersion(), closingAt);
        if (claimed != 1) {
            MonthlySeasonRow current = requireSeason(seasonId);
            return SeasonPhaseResult.notOwner(
                seasonId, current.getSnapshotId(), current.getStatus());
        }
        return SeasonPhaseResult.owner(seasonId, null, "CLOSING");
    }

    @Override
    public SeasonPhaseResult closeSeason(long seasonId, Date closingAt, String ownerInstance,
                                         int closeDrainSeconds, int leaseSeconds, int batchSize) {
        SeasonPhaseResult claim = closeSeason(seasonId, closingAt);
        if (claim.outcome() != SeasonPhaseResult.Outcome.OWNER) {
            return claim;
        }
        return buildSnapshot(seasonId, ownerInstance, closingAt, closeDrainSeconds,
            leaseSeconds, batchSize);
    }

    @Override
    public SeasonPhaseResult buildSnapshot(long seasonId, String ownerInstance, Date runAt,
                                           int closeDrainSeconds, int leaseSeconds, int batchSize) {
        validateBuildRequest(seasonId, ownerInstance, runAt, closeDrainSeconds, leaseSeconds, batchSize);
        MonthlySeasonRow season = requireSeason(seasonId);
        if (("REVIEW".equals(season.getStatus()) || "FINALIZED".equals(season.getStatus())
            || "REWARDED".equals(season.getStatus())) && season.getSnapshotId() != null) {
            monthlyRankingMapper.completeTerminalSnapshotJob(
                Long.toString(seasonId), now());
            return SeasonPhaseResult.completed(
                seasonId, season.getSnapshotId(), season.getStatus());
        }
        if (!"CLOSING".equals(season.getStatus())) {
            return SeasonPhaseResult.notOwner(
                seasonId, season.getSnapshotId(), season.getStatus());
        }
        if (season.getClosingAt() == null) {
            throw new IllegalStateException("Kỳ CLOSING không có thời điểm bắt đầu đóng");
        }
        Instant drainReadyAt = season.getClosingAt().toInstant().plusSeconds(closeDrainSeconds);
        if (runAt.toInstant().isBefore(drainReadyAt)) {
            return SeasonPhaseResult.waiting(seasonId, season.getSnapshotId(), season.getStatus());
        }

        String scopeKey = Long.toString(seasonId);
        Date staleBefore = Date.from(runAt.toInstant().minusSeconds(leaseSeconds));
        boolean claimed = monthlyRankingMapper.insertJobRunIgnore(
            SNAPSHOT_JOB, SEASON_SCOPE, scopeKey, ownerInstance, runAt) == 1;
        if (!claimed) {
            claimed = monthlyRankingMapper.claimStaleJobRun(SNAPSHOT_JOB, SEASON_SCOPE, scopeKey,
                ownerInstance, runAt, staleBefore) == 1;
        }
        if (!claimed) {
            return SeasonPhaseResult.notOwner(
                seasonId, season.getSnapshotId(), season.getStatus());
        }

        try {
            monthlyRankingMapper.insertInitialSnapshot(seasonId, season.getVoteCutoffAt());
            MonthlyRankSnapshotRow snapshot = requireInitialSnapshot(seasonId);
            if ("BUILDING".equals(snapshot.getStatus())) {
                appendSourceBatches(season, snapshot, ownerInstance, batchSize);
                SnapshotDigest digest = digestSnapshot(snapshot.getId(), batchSize);
                Date sealedAt = now();
                if (monthlyRankingMapper.sealSnapshot(snapshot.getId(), digest.entryCount(),
                    digest.totalTickets(), digest.contentHash(), sealedAt) != 1) {
                    validateExistingSeal(snapshot.getId(), digest);
                }
            } else if (!"SEALED".equals(snapshot.getStatus())) {
                throw new IllegalStateException("Snapshot hiện tại không thể dùng để chốt kỳ");
            }

            MonthlySeasonRow current = requireSeason(seasonId);
            Date reviewAt = now();
            if ("CLOSING".equals(current.getStatus())) {
                if (monthlyRankingMapper.claimSeasonReview(seasonId, current.getVersion(),
                    snapshot.getId(), reviewAt) != 1) {
                    throw new IllegalStateException("Mất quyền chuyển kỳ sang REVIEW");
                }
            } else if (!"REVIEW".equals(current.getStatus())
                || !Objects.equals(current.getSnapshotId(), snapshot.getId())) {
                throw new IllegalStateException("Kỳ đã đổi trạng thái ngoài state machine snapshot");
            }
            Date finishedAt = now();
            if (monthlyRankingMapper.completeJobRun(SNAPSHOT_JOB, SEASON_SCOPE, scopeKey,
                ownerInstance, finishedAt) != 1) {
                throw new IllegalStateException("Không thể hoàn tất job snapshot xếp hạng");
            }
            return SeasonPhaseResult.owner(seasonId, snapshot.getId(), "REVIEW");
        } catch (RuntimeException exception) {
            Date failedAt = now();
            monthlyRankingMapper.failJobRun(SNAPSHOT_JOB, SEASON_SCOPE, scopeKey, ownerInstance,
                failedAt, errorMessage(exception));
            throw exception;
        }
    }

    @Override
    public MonthlyRankingPage getRanking(Long seasonId, String periodCode, int page, int pageSize, Date at) {
        Objects.requireNonNull(at, "Thiếu thời điểm đọc bảng xếp hạng");
        if (seasonId != null && periodCode != null && !periodCode.isBlank()) {
            throw new IllegalArgumentException("Chỉ được chọn seasonId hoặc period");
        }
        if (seasonId != null && seasonId <= 0) {
            throw new IllegalArgumentException("Mã kỳ xếp hạng Ngọn Đuốc không hợp lệ");
        }
        String normalizedPeriod = normalizePeriod(periodCode);
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        long offset = Math.multiplyExact((long) safePage - 1, safePageSize);
        MonthlySeasonRow season = seasonId != null
            ? monthlyRankingMapper.selectSeasonById(seasonId)
            : normalizedPeriod == null
                ? monthlyRankingMapper.selectSeasonAt(at)
                : monthlyRankingMapper.selectSeasonByPeriod(normalizedPeriod);
        if (season == null && seasonId == null && normalizedPeriod == null) {
            season = monthlyRankingMapper.selectLatestSeason();
        }
        if (season == null) {
            return new MonthlyRankingPage(0, normalizedPeriod, "UNAVAILABLE", null,
                false, List.of(), 0, safePage, safePageSize);
        }

        boolean useSnapshot = season.getSnapshotId() != null
            && ("REVIEW".equals(season.getStatus()) || "FINALIZED".equals(season.getStatus())
                || "REWARDED".equals(season.getStatus()));
        long total = useSnapshot
            ? monthlyRankingMapper.countSnapshotRanking(season.getSnapshotId())
            : monthlyRankingMapper.countLiveRanking(season.getId());
        List<MonthlyRankRow> rows = useSnapshot
            ? monthlyRankingMapper.selectPublicSnapshotPage(
                season.getSnapshotId(), offset, safePageSize)
            : monthlyRankingMapper.selectLiveRankingPage(season.getId(), offset, safePageSize);
        if (!useSnapshot) {
            for (int index = 0; index < rows.size(); index++) {
                rows.get(index).setRankNo(Math.toIntExact(offset + index + 1));
            }
        }
        return new MonthlyRankingPage(season.getId(), season.getPeriodCode(), season.getStatus(),
            season.getVoteCutoffAt(), useSnapshot, rows, total, safePage, safePageSize);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SeasonPhaseResult pauseSnapshot(long seasonId, long operatorId, String reason, Date at) {
        requireSeasonId(seasonId);
        if (operatorId <= 0) {
            throw new IllegalArgumentException("Mã quản trị viên tạm dừng job không hợp lệ");
        }
        Objects.requireNonNull(at, "Thiếu thời điểm tạm dừng job snapshot");
        String normalizedReason = normalizeReason(reason);
        MonthlySeasonRow season = requireSeason(seasonId);
        if (!"CLOSING".equals(season.getStatus())) {
            return SeasonPhaseResult.notOwner(seasonId, season.getSnapshotId(), season.getStatus());
        }
        String auditReason = "PAUSED_BY_ADMIN:" + operatorId + ':' + normalizedReason;
        int changed = monthlyRankingMapper.pauseSnapshotJob(
            Long.toString(seasonId), at, auditReason);
        if (changed == 0) {
            changed = monthlyRankingMapper.insertPausedSnapshotJobIgnore(
                Long.toString(seasonId), at, auditReason);
        }
        return changed == 1
            ? SeasonPhaseResult.owner(seasonId, season.getSnapshotId(), "CLOSING")
            : SeasonPhaseResult.notOwner(seasonId, season.getSnapshotId(), "CLOSING");
    }

    @Override
    public SeasonPhaseResult retrySnapshot(long seasonId, String ownerInstance, Date runAt,
                                           int closeDrainSeconds, int leaseSeconds, int batchSize) {
        requireSeasonId(seasonId);
        Objects.requireNonNull(runAt, "Thiếu thời điểm retry snapshot");
        monthlyRankingMapper.resumeSnapshotJob(Long.toString(seasonId), runAt);
        return buildSnapshot(seasonId, ownerInstance, runAt, closeDrainSeconds, leaseSeconds, batchSize);
    }

    @Override
    public List<MonthlyRankDriftRow> reconcile(long seasonId) {
        requireSeasonId(seasonId);
        requireSeason(seasonId);
        return monthlyRankingMapper.checkRankCounterDrift(seasonId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public SeasonPhaseResult finalizeSeason(long seasonId, long operatorId, Date finalizedAt) {
        requireSeasonId(seasonId);
        if (operatorId <= 0) {
            throw new IllegalArgumentException("Mã quản trị viên chốt kỳ không hợp lệ");
        }
        Objects.requireNonNull(finalizedAt, "Thiếu thời điểm chốt kỳ");
        MonthlySeasonRow season = requireSeason(seasonId);
        if ("FINALIZED".equals(season.getStatus()) || "REWARDED".equals(season.getStatus())) {
            return SeasonPhaseResult.completed(
                seasonId, season.getSnapshotId(), season.getStatus());
        }
        if (!"REVIEW".equals(season.getStatus()) || season.getSnapshotId() == null) {
            return SeasonPhaseResult.notOwner(
                seasonId, season.getSnapshotId(), season.getStatus());
        }
        int claimed = monthlyRankingMapper.claimSeasonFinalized(seasonId, season.getVersion(),
            season.getSnapshotId(), operatorId, finalizedAt);
        return claimed == 1
            ? SeasonPhaseResult.owner(seasonId, season.getSnapshotId(), "FINALIZED")
            : SeasonPhaseResult.notOwner(seasonId, season.getSnapshotId(), "REVIEW");
    }

    private void appendSourceBatches(MonthlySeasonRow season, MonthlyRankSnapshotRow snapshot,
                                     String ownerInstance, int batchSize) {
        String scopeKey = Long.toString(season.getId());
        long offset = parseCheckpoint(monthlyRankingMapper.selectJobCheckpoint(
            SNAPSHOT_JOB, SEASON_SCOPE, scopeKey, ownerInstance));
        while (true) {
            List<MonthlyRankRow> rows = monthlyRankingMapper.selectSourceRankingPage(
                season.getId(), snapshot.getCutoffAt(), offset, batchSize);
            if (rows.isEmpty()) {
                return;
            }
            if (offset > Integer.MAX_VALUE - rows.size()) {
                throw new IllegalStateException("Số dòng snapshot vượt giới hạn hỗ trợ");
            }
            for (int index = 0; index < rows.size(); index++) {
                MonthlyRankRow row = rows.get(index);
                row.setSnapshotId(snapshot.getId());
                row.setRankNo(Math.toIntExact(offset + index + 1));
            }
            long nextOffset = Math.addExact(offset, rows.size());
            Date heartbeatAt = now();
            batchWriter.append(SNAPSHOT_JOB, SEASON_SCOPE, scopeKey, ownerInstance,
                rows, nextOffset, heartbeatAt);
            offset = nextOffset;
            if (rows.size() < batchSize) {
                return;
            }
        }
    }

    private SnapshotDigest digestSnapshot(long snapshotId, int batchSize) {
        MessageDigest digest = sha256Digest();
        int afterRank = 0;
        int entryCount = 0;
        long totalTickets = 0;
        while (true) {
            List<MonthlyRankRow> rows = monthlyRankingMapper.selectSnapshotEntryPage(
                snapshotId, afterRank, batchSize);
            if (rows.isEmpty()) {
                break;
            }
            for (MonthlyRankRow row : rows) {
                String canonical = row.getRankNo() + "|" + row.getBookId() + "|"
                    + Objects.toString(row.getAuthorId(), "-") + "|" + row.getTotalTickets() + "|"
                    + row.getDistinctVoterCount() + "|" + row.getLastVoteAt().getTime() + "\n";
                digest.update(canonical.getBytes(StandardCharsets.UTF_8));
                entryCount = Math.addExact(entryCount, 1);
                totalTickets = Math.addExact(totalTickets, row.getTotalTickets());
                afterRank = row.getRankNo();
            }
            if (rows.size() < batchSize) {
                break;
            }
        }
        return new SnapshotDigest(entryCount, totalTickets,
            HexFormat.of().formatHex(digest.digest()));
    }

    private void validateExistingSeal(long snapshotId, SnapshotDigest expected) {
        MonthlyRankSnapshotRow current = requireInitialSnapshotById(snapshotId);
        if (!"SEALED".equals(current.getStatus())
            || !Objects.equals(current.getContentHash(), expected.contentHash())
            || !Objects.equals(current.getEntryCount(), expected.entryCount())
            || !Objects.equals(current.getTotalTickets(), expected.totalTickets())) {
            throw new IllegalStateException("Snapshot đã bị niêm phong với nội dung khác");
        }
    }

    private MonthlyRankSnapshotRow requireInitialSnapshot(long seasonId) {
        MonthlyRankSnapshotRow snapshot = monthlyRankingMapper.selectInitialSnapshot(seasonId);
        if (snapshot == null) {
            throw new IllegalStateException("Không đọc được snapshot xếp hạng của kỳ");
        }
        return snapshot;
    }

    private MonthlyRankSnapshotRow requireInitialSnapshotById(long snapshotId) {
        MonthlyRankSnapshotRow snapshot = monthlyRankingMapper.selectInitialSnapshotById(snapshotId);
        if (snapshot == null) {
            throw new IllegalStateException("Snapshot xếp hạng không tồn tại");
        }
        return snapshot;
    }

    private long parseCheckpoint(String checkpoint) {
        if (checkpoint == null || checkpoint.isBlank()) {
            return 0;
        }
        long value = Long.parseLong(checkpoint);
        if (value < 0) {
            throw new IllegalStateException("Checkpoint snapshot không hợp lệ");
        }
        return value;
    }

    private int safeSeasonLimit(int limit) {
        return Math.max(1, Math.min(limit, MAX_SEASON_BATCH_SIZE));
    }

    private String normalizePeriod(String periodCode) {
        if (periodCode == null || periodCode.isBlank()) {
            return null;
        }
        String normalized = periodCode.trim();
        if (!normalized.matches("\\d{4}-(0[1-9]|1[0-2])")) {
            throw new IllegalArgumentException("Mã kỳ xếp hạng phải có dạng yyyy-MM");
        }
        return normalized;
    }

    private String normalizeReason(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        if (normalized.length() < 10 || normalized.length() > 255) {
            throw new IllegalArgumentException("Lý do thao tác phải dài từ 10 đến 255 ký tự");
        }
        return normalized;
    }

    private MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", exception);
        }
    }

    private void validateBuildRequest(long seasonId, String ownerInstance, Date runAt,
                                      int closeDrainSeconds, int leaseSeconds, int batchSize) {
        requireSeasonId(seasonId);
        if (ownerInstance == null || ownerInstance.isBlank() || ownerInstance.length() > 64) {
            throw new IllegalArgumentException("Mã instance chạy snapshot không hợp lệ");
        }
        Objects.requireNonNull(runAt, "Thiếu thời điểm chạy snapshot");
        if (closeDrainSeconds < 0 || leaseSeconds <= 0 || batchSize <= 0 || batchSize > 5_000) {
            throw new IllegalArgumentException("Cấu hình job snapshot không hợp lệ");
        }
    }

    private String errorMessage(RuntimeException exception) {
        String message = exception.getClass().getSimpleName() + ": "
            + (exception.getMessage() == null ? "không có thông tin" : exception.getMessage());
        return message.length() <= 500 ? message : message.substring(0, 500);
    }

    private Date now() {
        return Date.from(clock.instant());
    }

    private record SnapshotDigest(int entryCount, long totalTickets, String contentHash) {
    }

    private MonthlySeasonRow requireSeason(long seasonId) {
        MonthlySeasonRow season = monthlyRankingMapper.selectSeasonById(seasonId);
        if (season == null) {
            throw new IllegalArgumentException("Kỳ xếp hạng Ngọn Đuốc không tồn tại");
        }
        return season;
    }

    private void requireSeasonId(long seasonId) {
        if (seasonId <= 0) {
            throw new IllegalArgumentException("Mã kỳ xếp hạng Ngọn Đuốc không hợp lệ");
        }
    }
}
