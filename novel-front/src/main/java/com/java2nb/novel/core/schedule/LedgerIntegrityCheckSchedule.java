package com.java2nb.novel.core.schedule;

import com.java2nb.novel.mapper.WalletLedgerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class LedgerIntegrityCheckSchedule {

    private final WalletLedgerMapper walletLedgerMapper;

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
    }

    public boolean performAuditCheck() {
        List<Map<String, Object>> nonZeroSumTxs = walletLedgerMapper.checkZeroSumLedger();
        List<Map<String, Object>> mismatches = walletLedgerMapper.checkProjectionMismatch();
        return nonZeroSumTxs.isEmpty() && mismatches.isEmpty();
    }
}
