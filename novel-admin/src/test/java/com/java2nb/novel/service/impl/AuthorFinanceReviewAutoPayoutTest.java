package com.java2nb.novel.service.impl;

import com.java2nb.common.security.AdminPiiCryptoService;
import com.java2nb.novel.dao.AuthorFinanceReviewDao;
import com.java2nb.novel.domain.AuthorWithdrawalReviewDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class AuthorFinanceReviewAutoPayoutTest {

    private AuthorFinanceReviewDao dao;
    private AdminPiiCryptoService piiCryptoService;
    private AuthorFinanceReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        dao = mock(AuthorFinanceReviewDao.class);
        piiCryptoService = mock(AdminPiiCryptoService.class);
        service = new AuthorFinanceReviewServiceImpl(dao, piiCryptoService);
    }

    @Test
    void testExecuteAutoPayoutRequiresApprovedState() {
        long withdrawalId = 10L;
        AuthorWithdrawalReviewDO withdrawal = new AuthorWithdrawalReviewDO();
        withdrawal.setId(withdrawalId);
        withdrawal.setStatus("PENDING_REVIEW");

        when(dao.getWithdrawal(withdrawalId)).thenReturn(withdrawal);

        assertThatThrownBy(() -> service.executeAutoPayout(withdrawalId, 1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("APPROVED");
    }

    @Test
    void testExecuteAutoPayoutFailsBeforeStateChangeWhenProviderMissing() {
        long withdrawalId = 10L;
        AuthorWithdrawalReviewDO withdrawal = new AuthorWithdrawalReviewDO();
        withdrawal.setId(withdrawalId);
        withdrawal.setWithdrawalNo("WD100");
        withdrawal.setStatus("APPROVED");
        withdrawal.setVersion(1L);
        withdrawal.setGrossAmountVnd(1000000L);
        withdrawal.setWithheldTaxVnd(50000L);
        withdrawal.setNetAmountVnd(950000L);
        withdrawal.setBankAccountCiphertext("encAccount");
        withdrawal.setBankAccountNameCiphertext("encName");
        withdrawal.setBankCode("970422");

        when(dao.getWithdrawal(withdrawalId)).thenReturn(withdrawal);
        when(piiCryptoService.decrypt("encAccount")).thenReturn("123456789");
        when(piiCryptoService.decrypt("encName")).thenReturn("NGUYEN VAN A");

        when(dao.insertWithdrawalAudit(any(), any(), any(), anyLong(), any())).thenReturn(1);

        assertThatThrownBy(() -> service.executeAutoPayout(withdrawalId, 1L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("adapter payout");

        verify(dao, never()).markWithdrawalProcessing(anyLong(), anyLong(), anyLong());
        verify(dao, never()).requestWithdrawalSettlement(anyLong(), anyLong(), anyLong(), anyString());
    }
}
