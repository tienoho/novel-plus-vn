package com.java2nb.novel.service.impl;

import com.java2nb.novel.core.config.AuthorPayoutProperties;
import com.java2nb.novel.core.config.PiiCryptoProperties;
import com.java2nb.novel.mapper.AuthorFinanceMapper;
import com.java2nb.novel.service.finance.AuthorKycRow;
import com.java2nb.novel.service.finance.AuthorWithdrawalRow;
import com.java2nb.novel.service.finance.KycSubmissionRequest;
import com.java2nb.novel.service.finance.PiiCryptoService;
import com.java2nb.novel.service.finance.WithdrawalRequestInput;
import com.java2nb.novel.service.wallet.InsufficientWalletBalanceException;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import com.java2nb.novel.service.wallet.WalletPostResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.util.Base64;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthorFinanceServiceImplTest {

    private AuthorFinanceMapper mapper;
    private WalletLedgerService walletLedgerService;
    private AuthorFinanceServiceImpl service;

    @BeforeEach
    void setUp() {
        mapper = mock(AuthorFinanceMapper.class);
        walletLedgerService = mock(WalletLedgerService.class);
        PiiCryptoProperties piiProperties = new PiiCryptoProperties();
        piiProperties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        AuthorPayoutProperties payoutProperties = new AuthorPayoutProperties();
        payoutProperties.setEnabled(true);
        payoutProperties.setVndPerXu(10);
        payoutProperties.setMinimumXu(100);
        payoutProperties.setRequestOpenDay(1);
        payoutProperties.setRequestCloseDay(28);
        service = new AuthorFinanceServiceImpl(mapper, walletLedgerService,
            new PiiCryptoService(piiProperties), payoutProperties);
    }

    @Test
    void encryptsKycAndOnlyReturnsMaskedValues() {
        AuthorKycRow saved = kycRow("PENDING");
        when(mapper.selectKycByAuthorId(22L)).thenReturn(null, saved);
        when(mapper.insertKyc(any(AuthorKycRow.class))).thenReturn(1);
        when(mapper.insertKycAudit(anyLong(), anyLong(), anyLong(), anyString(), any(), anyString(), anyString(),
            anyLong(), any())).thenReturn(1);

        var status = service.submitKyc(22L, 11L, validKyc());

        ArgumentCaptor<AuthorKycRow> captor = ArgumentCaptor.forClass(AuthorKycRow.class);
        verify(mapper).insertKyc(captor.capture());
        AuthorKycRow encrypted = captor.getValue();
        assertThat(encrypted.getIdentityNumberCiphertext()).startsWith("v1:").doesNotContain("001234567890");
        assertThat(encrypted.getBankAccountCiphertext()).startsWith("v1:").doesNotContain("1234567890");
        assertThat(encrypted.getIdentityNumberHash()).hasSize(64);
        assertThat(status.identityNumberMasked()).isEqualTo("****7890");
        assertThat(status.bankAccountMasked()).isEqualTo("****7890");
    }

    @Test
    void rejectsUnderageOrUnconsentedKyc() {
        KycSubmissionRequest request = validKyc();
        request.setDateOfBirth(LocalDate.now().minusYears(17));

        assertThatThrownBy(() -> service.submitKyc(22L, 11L, request))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("18 tuổi");

        verify(mapper, never()).insertKyc(any());
    }

    @Test
    void createsIdempotentWithdrawalAndHoldsAuthorXu() {
        AuthorKycRow verified = kycRow("VERIFIED");
        AuthorWithdrawalRow saved = withdrawal("withdrawal-request-001", 500L);
        when(mapper.selectWithdrawalByIdempotencyKey("withdrawal-request-001")).thenReturn(null, saved);
        when(mapper.selectKycByAuthorId(22L)).thenReturn(verified);
        when(walletLedgerService.getAuthorAvailableBalance(22L)).thenReturn(1_000L);
        when(mapper.insertWithdrawal(anyString(), anyString(), anyLong(), anyLong(), anyLong(), anyLong(),
            anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyString())).thenReturn(1);
        when(walletLedgerService.holdAuthorWithdrawal(eq(22L), eq(500L), anyString(), anyString()))
            .thenReturn(WalletPostResult.POSTED);
        when(mapper.insertWithdrawalAudit(anyLong(), anyString(), anyString(), any(), anyString(), anyString(),
            anyLong(), any())).thenReturn(1);
        WithdrawalRequestInput input = new WithdrawalRequestInput();
        input.setAmountXu(500L);
        input.setIdempotencyKey("withdrawal-request-001");

        AuthorWithdrawalRow result = service.requestWithdrawal(22L, 11L, input);

        assertThat(result).isSameAs(saved);
        verify(mapper).insertWithdrawal(anyString(), eq("withdrawal-request-001"), eq(22L), eq(11L), eq(9L),
            eq(500L), eq(10L), eq(5_000L), eq("VCB"), anyString(), eq("7890"), anyString(), anyString());
        verify(walletLedgerService).holdAuthorWithdrawal(eq(22L), eq(500L), anyString(), anyString());

        when(mapper.selectWithdrawalByIdempotencyKey("withdrawal-request-001")).thenReturn(saved);
        assertThat(service.requestWithdrawal(22L, 11L, input)).isSameAs(saved);
        verify(walletLedgerService, org.mockito.Mockito.times(1))
            .holdAuthorWithdrawal(eq(22L), eq(500L), anyString(), anyString());
    }

    @Test
    void rejectsWithdrawalWhenAuthorRevenueIsInsufficient() {
        when(mapper.selectWithdrawalByIdempotencyKey(anyString())).thenReturn(null);
        when(mapper.selectKycByAuthorId(22L)).thenReturn(kycRow("VERIFIED"));
        when(walletLedgerService.getAuthorAvailableBalance(22L)).thenReturn(99L);
        WithdrawalRequestInput input = new WithdrawalRequestInput();
        input.setAmountXu(500L);
        input.setIdempotencyKey("withdrawal-request-002");

        assertThatThrownBy(() -> service.requestWithdrawal(22L, 11L, input))
            .isInstanceOf(InsufficientWalletBalanceException.class);

        verify(mapper, never()).insertWithdrawal(anyString(), anyString(), anyLong(), anyLong(), anyLong(),
            anyLong(), anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    private KycSubmissionRequest validKyc() {
        KycSubmissionRequest request = new KycSubmissionRequest();
        request.setLegalName("Nguyễn Văn Tác Giả");
        request.setDateOfBirth(LocalDate.of(1990, 1, 1));
        request.setIdentityType("CCCD");
        request.setIdentityNumber("001234567890");
        request.setTaxCode("0123456789");
        request.setBankCode("VCB");
        request.setBankAccount("1234567890");
        request.setBankAccountName("NGUYEN VAN TAC GIA");
        request.setConsentVersion(AuthorFinanceServiceImpl.CURRENT_CONSENT_VERSION);
        return request;
    }

    private AuthorKycRow kycRow(String status) {
        AuthorKycRow row = new AuthorKycRow();
        row.setId(9L);
        row.setAuthorId(22L);
        row.setUserId(11L);
        row.setIdentityType("CCCD");
        row.setIdentityNumberLast4("7890");
        row.setBankCode("VCB");
        row.setBankAccountLast4("7890");
        row.setBankAccountCiphertext("v1:bank-account");
        row.setBankAccountNameCiphertext("v1:bank-name");
        row.setSubmissionVersion(1);
        row.setSubmittedAt(new Date());
        row.setStatus(status);
        return row;
    }

    private AuthorWithdrawalRow withdrawal(String idempotencyKey, long amountXu) {
        AuthorWithdrawalRow row = new AuthorWithdrawalRow();
        row.setId(10L);
        row.setWithdrawalNo("WD-test");
        row.setIdempotencyKey(idempotencyKey);
        row.setAuthorId(22L);
        row.setUserId(11L);
        row.setRequestedXu(amountXu);
        row.setStatus("PENDING_REVIEW");
        return row;
    }
}
