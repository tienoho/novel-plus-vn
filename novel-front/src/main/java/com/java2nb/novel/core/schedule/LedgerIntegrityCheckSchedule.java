package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.GamificationProperties;
import com.java2nb.novel.mapper.MonthlyTicketMapper;
import com.java2nb.novel.mapper.MonthlyRankingMapper;
import com.java2nb.novel.mapper.WalletLedgerMapper;
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
    private final Clock clock;

    @Autowired
    public LedgerIntegrityCheckSchedule(WalletLedgerMapper walletLedgerMapper,
                                        MonthlyTicketMapper monthlyTicketMapper,
                                        MonthlyRankingMapper monthlyRankingMapper,
                                        GamificationProperties gamificationProperties) {
        this(walletLedgerMapper, monthlyTicketMapper, monthlyRankingMapper, gamificationProperties,
            Clock.systemUTC());
    }

    LedgerIntegrityCheckSchedule(WalletLedgerMapper walletLedgerMapper,
                                 MonthlyTicketMapper monthlyTicketMapper,
                                 MonthlyRankingMapper monthlyRankingMapper,
                                 GamificationProperties gamificationProperties,
                                 Clock clock) {
        this.walletLedgerMapper = walletLedgerMapper;
        this.monthlyTicketMapper = monthlyTicketMapper;
        this.monthlyRankingMapper = monthlyRankingMapper;
        this.gamificationProperties = gamificationProperties;
        this.clock = clock;
    }

    @Scheduled(cron = "${ledger.integrity.cron:0 0 3 * * ?}") // Hàng ngày vào 03:00 AM
    public void runLedgerIntegrityAudit() {
        log.info("Bắt đầu kiểm tra tự động toàn vẹn sổ cái kép và projection số dư...");

        List<Map<String, Object>> nonZeroSumTxs = walletLedgerMapper.checkZeroSumLedger();
        if (!nonZeroSumTxs.isEmpty()) {
            log.error("CẢNH BÁO NHIÊM TRỌNG: Phát hiện {} giao dịch sổ cái KHÔNG CÂN BẰNG ZERO-SUM: {}",
                nonZeroSumTxs.size(), nonZeroSumTxs);
        } else {
            log.info("Kiểm tra zero-sum sổ cái kép: HOÀN HẢO (100% giao dịch cân bằng)");
        }

        List<Map<String, Object>> mismatches = walletLedgerMapper.checkProjectionMismatch();
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
        logRows("GAMIFY-ALERT-001", "projection tài khoản lệch tổng lot còn lại",
            monthlyTicketMapper.checkAccountLotDrift());
        logRows("GAMIFY-ALERT-002", "phân bổ lot lệch bút toán Ngọn Đuốc",
            monthlyTicketMapper.checkAllocationImbalance());
        logRows("GAMIFY-ALERT-003", "lot không có bút toán cấp hợp lệ",
            monthlyTicketMapper.checkOrphanLots());
        if (!monthlyRankingMapper.checkRankCounterDrift(null).isEmpty()) {
            log.error("GAMIFY-ALERT-004 bộ đếm xếp hạng lệch vote nguồn");
        }

        List<Map<String, Object>> stuckRows = monthlyTicketMapper.checkStuckJobs(
            jobLeaseCutoff(), reviewWindowCutoff());
        logRows("GAMIFY-ALERT-005", "job gamification hết lease",
            stuckRows.stream().filter(row -> "STALE_JOB".equals(row.get("alert_type"))).toList());
        logRows("GAMIFY-ALERT-006", "kỳ xếp hạng ở REVIEW quá hạn",
            stuckRows.stream().filter(row -> "REVIEW_OVERDUE".equals(row.get("alert_type"))).toList());
        logRows("GAMIFY-ALERT-007", "thưởng tác giả chờ release quá hạn",
            monthlyTicketMapper.checkPendingRewards(claimWindowCutoff()));
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
