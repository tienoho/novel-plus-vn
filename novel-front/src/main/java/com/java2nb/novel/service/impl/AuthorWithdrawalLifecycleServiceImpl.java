package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.AuthorFinanceMapper;
import com.java2nb.novel.service.finance.AuthorWithdrawalLifecycleService;
import com.java2nb.novel.service.finance.AuthorWithdrawalRow;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
public class AuthorWithdrawalLifecycleServiceImpl implements AuthorWithdrawalLifecycleService {

    private static final Set<String> RELEASE_TARGETS = Set.of("REJECTED", "FAILED", "CANCELLED");

    private final AuthorFinanceMapper authorFinanceMapper;
    private final WalletLedgerService walletLedgerService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean process(AuthorWithdrawalRow withdrawal) {
        if (withdrawal == null || withdrawal.getId() == null || withdrawal.getVersion() == null) {
            throw new IllegalArgumentException("Thiếu dữ liệu vòng đời yêu cầu rút");
        }
        String fromStatus = withdrawal.getStatus();
        if (!"RELEASE_PENDING".equals(fromStatus) && !"SETTLEMENT_PENDING".equals(fromStatus)) {
            return false;
        }
        if (authorFinanceMapper.claimWithdrawalAction(withdrawal.getId(), fromStatus,
            withdrawal.getVersion()) != 1) {
            return false;
        }

        long claimedVersion = withdrawal.getVersion() + 1;
        String finalStatus;
        String eventType;
        if ("RELEASE_PENDING".equals(fromStatus)) {
            finalStatus = withdrawal.getReleaseTargetStatus();
            if (!RELEASE_TARGETS.contains(finalStatus)) {
                throw new IllegalStateException("Trạng thái cuối khi hoàn hold không hợp lệ");
            }
            walletLedgerService.reverseTransaction(withdrawal.getHoldIdempotencyKey(),
                "AUTHOR_WITHDRAWAL_RELEASE", withdrawal.getWithdrawalNo(),
                "AUTHOR_WITHDRAWAL_RELEASE:" + withdrawal.getWithdrawalNo(),
                "Hoàn Xu từ yêu cầu rút không được thực hiện");
            eventType = "FUNDS_RELEASED";
        } else {
            finalStatus = "PAID";
            walletLedgerService.settleAuthorWithdrawal(withdrawal.getRequestedXu(), withdrawal.getWithdrawalNo(),
                "AUTHOR_WITHDRAWAL_SETTLED:" + withdrawal.getWithdrawalNo());
            eventType = "LEDGER_SETTLED";
        }

        if (authorFinanceMapper.completeWithdrawalAction(withdrawal.getId(), fromStatus, claimedVersion,
            finalStatus) != 1) {
            throw new IllegalStateException("Không thể hoàn tất trạng thái yêu cầu rút");
        }
        if (authorFinanceMapper.insertWithdrawalAudit(withdrawal.getId(), withdrawal.getWithdrawalNo(), eventType,
            fromStatus, finalStatus, "SYSTEM", null, null) != 1) {
            throw new IllegalStateException("Không thể ghi audit vòng đời yêu cầu rút");
        }
        return true;
    }
}
