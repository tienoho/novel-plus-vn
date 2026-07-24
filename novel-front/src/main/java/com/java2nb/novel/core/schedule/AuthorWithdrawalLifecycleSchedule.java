package com.java2nb.novel.core.schedule;

import com.java2nb.novel.mapper.AuthorFinanceMapper;
import com.java2nb.novel.service.finance.AuthorWithdrawalLifecycleService;
import com.java2nb.novel.service.finance.AuthorWithdrawalRow;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class AuthorWithdrawalLifecycleSchedule {

    private final AuthorFinanceMapper authorFinanceMapper;
    private final AuthorWithdrawalLifecycleService lifecycleService;

    @Scheduled(fixedDelayString = "${author.payout.lifecycle-delay-ms:60000}",
        initialDelayString = "${author.payout.lifecycle-initial-delay-ms:30000}")
    public void processPendingActions() {
        List<AuthorWithdrawalRow> withdrawals = authorFinanceMapper.listPendingWithdrawalActions(50);
        for (AuthorWithdrawalRow withdrawal : withdrawals) {
            try {
                lifecycleService.process(withdrawal);
            } catch (RuntimeException exception) {
                log.error("Không thể xử lý vòng đời yêu cầu rút {}", withdrawal.getWithdrawalNo(), exception);
            }
        }
    }
}
