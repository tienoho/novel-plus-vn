package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.GamificationProperties;
import com.java2nb.novel.core.observability.NovelBusinessMetrics;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.GamificationQueue;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.LedgerCheck;
import com.java2nb.novel.mapper.MonthlyTicketMapper;
import com.java2nb.novel.mapper.MonthlyRankingMapper;
import com.java2nb.novel.mapper.WalletLedgerMapper;
import com.java2nb.novel.service.gamification.MonthlyRankDriftRow;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Component
@Slf4j
public class LedgerIntegrityCheckSchedule {

    private final WalletLedgerMapper walletLedgerMapper;
    private final MonthlyTicketMapper monthlyTicketMapper;
    private final MonthlyRankingMapper monthlyRankingMapper;
    private final GamificationProperties gamificationProperties;
    private final NovelBusinessMetrics metrics;
    private final Clock clock;

    @Autowired
    public LedgerIntegrityCheckSchedule(WalletLedgerMapper walletLedgerMapper,
                                        MonthlyTicketMapper monthlyTicketMapper,
                                        MonthlyRankingMapper monthlyRankingMapper,
                                        GamificationProperties gamificationProperties,
                                        NovelBusinessMetrics metrics) {
        this(walletLedgerMapper, monthlyTicketMapper, monthlyRankingMapper, gamificationProperties,
            metrics, Clock.systemUTC());
    }

    LedgerIntegrityCheckSchedule(WalletLedgerMapper walletLedgerMapper,
                                 MonthlyTicketMapper monthlyTicketMapper,
                                 MonthlyRankingMapper monthlyRankingMapper,
                                 GamificationProperties gamificationProperties,
                                 NovelBusinessMetrics metrics,
                                 Clock clock) {
        this.walletLedgerMapper = walletLedgerMapper;
        this.monthlyTicketMapper = monthlyTicketMapper;
        this.monthlyRankingMapper = monthlyRankingMapper;
        this.gamificationProperties = gamificationProperties;
        this.metrics = metrics;
        this.clock = clock;
    }

    @Scheduled(cron = "${ledger.integrity.cron:0 0 3 * * ?}") // Hàng ngày vào 03:00 AM
    public void runLedgerIntegrityAudit() {
        log.info("Bắt đầu kiểm tra tự động toàn vẹn sổ cái kép và projection số dư...");

        List<Map<String, Object>> nonZeroSumTxs = walletLedgerMapper.checkZeroSumLedger();
        metrics.setLedgerMismatch(LedgerCheck.ZERO_SUM, nonZeroSumTxs.size());
        if (!nonZeroSumTxs.isEmpty()) {
            log.error("CẢNH BÁO NHIÊM TRỌNG: Phát hiện {} giao dịch sổ cái KHÔNG CÂN BẰNG ZERO-SUM: {}",
                nonZeroSumTxs.size(), nonZeroSumTxs);
        } else {
            log.info("Kiểm tra zero-sum sổ cái kép: HOÀN HẢO (100% giao dịch cân bằng)");
        }

        List<Map<String, Object>> mismatches = walletLedgerMapper.checkProjectionMismatch();
        metrics.setLedgerMismatch(LedgerCheck.WALLET_PROJECTION, mismatches.size());
        if (!mismatches.isEmpty()) {
            log.error("CẢNH BÁO NHIÊM TRỌNG: Phát hiện {} tài khoản bị LỆCH PROJECTION giữa user.account_balance và wallet_account: {}",
                mismatches.size(), mismatches);
        } else {
            log.info("Kiểm tra projection số dư người dùng: HOÀN HẢO (0% sai lệch)");
        }

        logGamificationAudit();
    }

    public boolean performAuditCheck() {
        List<Map<String, Object>> nonZeroSumTxs = walletLedgerMapper.checkZeroSumLedger();
        List<Map<String, Object>> mismatches = walletLedgerMapper.checkProjectionMismatch();
        List<Map<String, Object>> ticketDrift = monthlyTicketMapper.checkAccountLotDrift();
        List<Map<String, Object>> allocationImbalance = monthlyTicketMapper.checkAllocationImbalance();
        List<Map<String, Object>> orphanLots = monthlyTicketMapper.checkOrphanLots();
        List<Map<String, Object>> stuckJobs = monthlyTicketMapper.checkStuckJobs(
            jobLeaseCutoff(), reviewWindowCutoff());
        List<Map<String, Object>> pendingRewards = monthlyTicketMapper.checkPendingRewards(
            claimWindowCutoff());
        boolean rankConsistent = monthlyRankingMapper.checkRankCounterDrift(null).isEmpty();
        return nonZeroSumTxs.isEmpty() && mismatches.isEmpty() && ticketDrift.isEmpty()
            && allocationImbalance.isEmpty() && orphanLots.isEmpty() && stuckJobs.isEmpty()
            && pendingRewards.isEmpty() && rankConsistent;
    }

    private void logGamificationAudit() {
        List<Map<String, Object>> ticketDrift = monthlyTicketMapper.checkAccountLotDrift();
        List<Map<String, Object>> allocationImbalance = monthlyTicketMapper.checkAllocationImbalance();
        List<Map<String, Object>> orphanLots = monthlyTicketMapper.checkOrphanLots();
        List<MonthlyRankDriftRow> rankDrift = monthlyRankingMapper.checkRankCounterDrift(null);
        metrics.setLedgerMismatch(LedgerCheck.TICKET_PROJECTION, ticketDrift.size());
        metrics.setLedgerMismatch(LedgerCheck.TICKET_ALLOCATION, allocationImbalance.size());
        metrics.setLedgerMismatch(LedgerCheck.ORPHAN_LOT, orphanLots.size());
        metrics.setLedgerMismatch(LedgerCheck.RANK_COUNTER, rankDrift.size());
        logRows("GAMIFY-ALERT-001", "projection tài khoản lệch tổng lot còn lại", ticketDrift);
        logRows("GAMIFY-ALERT-002", "phân bổ lot lệch bút toán Ngọn Đuốc", allocationImbalance);
        logRows("GAMIFY-ALERT-003", "lot không có bút toán cấp hợp lệ", orphanLots);
        if (!rankDrift.isEmpty()) {
            log.error("GAMIFY-ALERT-004 bộ đếm xếp hạng lệch vote nguồn");
        }

        List<Map<String, Object>> stuckRows = monthlyTicketMapper.checkStuckJobs(
            jobLeaseCutoff(), reviewWindowCutoff());
        List<Map<String, Object>> staleJobs = stuckRows.stream()
            .filter(row -> "STALE_JOB".equals(row.get("alert_type"))).toList();
        List<Map<String, Object>> reviewOverdue = stuckRows.stream()
            .filter(row -> "REVIEW_OVERDUE".equals(row.get("alert_type"))).toList();
        List<Map<String, Object>> pendingRewards = monthlyTicketMapper.checkPendingRewards(claimWindowCutoff());
        metrics.setGamificationQueue(GamificationQueue.STALE_JOBS, staleJobs.size());
        metrics.setGamificationQueue(GamificationQueue.REVIEW_OVERDUE, reviewOverdue.size());
        metrics.setGamificationQueue(GamificationQueue.PENDING_REWARDS, pendingRewards.size());
        logRows("GAMIFY-ALERT-005", "job gamification hết lease", staleJobs);
        logRows("GAMIFY-ALERT-006", "kỳ xếp hạng ở REVIEW quá hạn", reviewOverdue);
        logRows("GAMIFY-ALERT-007", "thưởng tác giả chờ release quá hạn", pendingRewards);
    }

    /**
     * Cutoff tính ở Java rồi truyền thẳng xuống SQL dưới dạng tham số, không để SQL tự cộng trừ
     * bằng TIMESTAMPADD với tham số động — bộ phân tích SQL của ShardingSphere hiểu nhầm tên đơn
     * vị thời gian (SECOND/HOUR/DAY) thành tên cột khi giá trị đi kèm là một bind parameter.
     */
    private Date jobLeaseCutoff() {
        return Date.from(Instant.now(clock).minusSeconds(gamificationProperties.getJob().getLeaseSeconds()));
    }

    private Date reviewWindowCutoff() {
        return Date.from(Instant.now(clock)
            .minusSeconds(gamificationProperties.getSeason().getReviewWindowHours() * 3600L));
    }

    private Date claimWindowCutoff() {
        return Date.from(Instant.now(clock)
            .minusSeconds(gamificationProperties.getReward().getClaimWindowDays() * 86400L));
    }

    private void logRows(String alertCode, String description, List<Map<String, Object>> rows) {
        if (!rows.isEmpty()) {
            log.error("{} {}: số bản ghi={}, chi tiết={}", alertCode, description, rows.size(), rows);
        }
    }
}
