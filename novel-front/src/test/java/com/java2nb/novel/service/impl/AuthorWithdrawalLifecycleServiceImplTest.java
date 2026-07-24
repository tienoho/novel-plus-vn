package com.java2nb.novel.service.impl;

import com.java2nb.novel.mapper.AuthorFinanceMapper;
import com.java2nb.novel.service.finance.AuthorWithdrawalRow;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import com.java2nb.novel.service.wallet.WalletPostResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorWithdrawalLifecycleServiceImplTest {

    private AuthorFinanceMapper mapper;
    private WalletLedgerService walletLedgerService;
    private AuthorWithdrawalLifecycleServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(AuthorFinanceMapper.class);
        walletLedgerService = mock(WalletLedgerService.class);
        service = new AuthorWithdrawalLifecycleServiceImpl(mapper, walletLedgerService);
        when(mapper.insertWithdrawalAudit(org.mockito.ArgumentMatchers.anyLong(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.isNull(),
            org.mockito.ArgumentMatchers.isNull())).thenReturn(1);
    }

    @Test
    void releasesHeldXuForRejectedWithdrawal() {
        AuthorWithdrawalRow withdrawal = withdrawal("RELEASE_PENDING", 3L);
        withdrawal.setReleaseTargetStatus("REJECTED");
        when(mapper.claimWithdrawalAction(10L, "RELEASE_PENDING", 3L)).thenReturn(1);
        when(mapper.completeWithdrawalAction(10L, "RELEASE_PENDING", 4L, "REJECTED")).thenReturn(1);
        when(walletLedgerService.reverseTransaction("AUTHOR_WITHDRAWAL_HOLD:WD-1",
            "AUTHOR_WITHDRAWAL_RELEASE", "WD-1", "AUTHOR_WITHDRAWAL_RELEASE:WD-1",
            "Hoàn Xu từ yêu cầu rút không được thực hiện")).thenReturn(WalletPostResult.POSTED);

        assertThat(service.process(withdrawal)).isTrue();

        verify(walletLedgerService).reverseTransaction("AUTHOR_WITHDRAWAL_HOLD:WD-1",
            "AUTHOR_WITHDRAWAL_RELEASE", "WD-1", "AUTHOR_WITHDRAWAL_RELEASE:WD-1",
            "Hoàn Xu từ yêu cầu rút không được thực hiện");
        verify(mapper).insertWithdrawalAudit(10L, "WD-1", "FUNDS_RELEASED", "RELEASE_PENDING", "REJECTED",
            "SYSTEM", null, null);
    }

    @Test
    void settlesClearingAfterBankPayment() {
        AuthorWithdrawalRow withdrawal = withdrawal("SETTLEMENT_PENDING", 7L);
        when(mapper.claimWithdrawalAction(10L, "SETTLEMENT_PENDING", 7L)).thenReturn(1);
        when(mapper.completeWithdrawalAction(10L, "SETTLEMENT_PENDING", 8L, "PAID")).thenReturn(1);
        when(walletLedgerService.settleAuthorWithdrawal(200L, "WD-1", "AUTHOR_WITHDRAWAL_SETTLED:WD-1"))
            .thenReturn(WalletPostResult.POSTED);

        assertThat(service.process(withdrawal)).isTrue();

        verify(walletLedgerService).settleAuthorWithdrawal(200L, "WD-1", "AUTHOR_WITHDRAWAL_SETTLED:WD-1");
        verify(mapper).insertWithdrawalAudit(10L, "WD-1", "LEDGER_SETTLED", "SETTLEMENT_PENDING", "PAID",
            "SYSTEM", null, null);
    }

    @Test
    void replicaThatLosesClaimDoesNotTouchLedger() {
        AuthorWithdrawalRow withdrawal = withdrawal("RELEASE_PENDING", 3L);
        withdrawal.setReleaseTargetStatus("FAILED");
        when(mapper.claimWithdrawalAction(10L, "RELEASE_PENDING", 3L)).thenReturn(0);

        assertThat(service.process(withdrawal)).isFalse();

        verify(walletLedgerService, never()).reverseTransaction(org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
            org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
    }

    private AuthorWithdrawalRow withdrawal(String status, long version) {
        AuthorWithdrawalRow row = new AuthorWithdrawalRow();
        row.setId(10L);
        row.setWithdrawalNo("WD-1");
        row.setHoldIdempotencyKey("AUTHOR_WITHDRAWAL_HOLD:WD-1");
        row.setRequestedXu(200L);
        row.setStatus(status);
        row.setVersion(version);
        return row;
    }
}
