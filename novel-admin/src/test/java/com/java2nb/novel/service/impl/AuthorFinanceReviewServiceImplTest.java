package com.java2nb.novel.service.impl;

import com.java2nb.common.security.AdminPiiCryptoService;
import com.java2nb.novel.dao.AuthorFinanceReviewDao;
import com.java2nb.novel.domain.AuthorKycReviewDO;
import com.java2nb.novel.domain.AuthorWithdrawalReviewDO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.lang.reflect.Method;
import java.util.Base64;

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

class AuthorFinanceReviewServiceImplTest {

    private AuthorFinanceReviewDao dao;
    private AuthorFinanceReviewServiceImpl service;

    @BeforeEach
    void setUp() {
        dao = mock(AuthorFinanceReviewDao.class);
        String key = Base64.getEncoder().encodeToString(new byte[32]);
        service = new AuthorFinanceReviewServiceImpl(dao, new AdminPiiCryptoService(key));
        when(dao.insertKycAudit(any(), anyString(), anyString(), anyLong(), any())).thenReturn(1);
        when(dao.insertWithdrawalAudit(any(), anyString(), anyString(), anyLong(), any())).thenReturn(1);
    }

    @Test
    void approvesOnlyExpectedPendingKycVersion() {
        AuthorKycReviewDO profile = kyc();
        when(dao.getKyc(1L)).thenReturn(profile);
        when(dao.reviewKyc(1L, 2, "VERIFIED", 99L, null)).thenReturn(1);

        service.approveKyc(1L, 2, 99L);

        verify(dao).insertKycAudit(profile, "REVIEW_APPROVED", "VERIFIED", 99L, null);
        assertThatThrownBy(() -> service.approveKyc(1L, 1, 99L))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsWithdrawalByRequestingLedgerRelease() {
        AuthorWithdrawalReviewDO withdrawal = withdrawal("PENDING_REVIEW", 3L);
        when(dao.getWithdrawal(10L)).thenReturn(withdrawal);
        when(dao.requestWithdrawalRelease(10L, "PENDING_REVIEW", 3L, "REJECTED", 99L,
            "Thông tin ngân hàng không khớp")).thenReturn(1);

        service.rejectWithdrawal(10L, 3L, "Thông tin ngân hàng không khớp", 99L);

        verify(dao).insertWithdrawalAudit(withdrawal, "REJECTION_REQUESTED", "RELEASE_PENDING", 99L,
            "Thông tin ngân hàng không khớp");
    }

    @Test
    void recordsBankReferenceBeforeLedgerSettlement() {
        AuthorWithdrawalReviewDO withdrawal = withdrawal("PROCESSING", 5L);
        setActor(withdrawal, "setApprovedBy", 88L);
        setActor(withdrawal, "setExecutedBy", 99L);
        when(dao.getWithdrawal(10L)).thenReturn(withdrawal);
        when(dao.requestWithdrawalSettlement(10L, 5L, 99L, "BANK-REF-001")).thenReturn(1);

        service.markPaid(10L, 5L, "BANK-REF-001", 99L);

        verify(dao).insertWithdrawalAudit(withdrawal, "PAYMENT_RECORDED", "SETTLEMENT_PENDING", 99L,
            "BANK-REF-001");
    }

    @Test
    void refusesApproverExecutingTheSameWithdrawalBeforeCallingMapper() {
        AuthorWithdrawalReviewDO withdrawal = withdrawal("APPROVED", 4L);
        setActor(withdrawal, "setApprovedBy", 99L);
        when(dao.getWithdrawal(10L)).thenReturn(withdrawal);
        when(dao.markWithdrawalProcessing(10L, 4L, 99L)).thenReturn(1);

        assertThatThrownBy(() -> service.markProcessing(10L, 4L, 99L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("khác người duyệt");

        verify(dao, never()).markWithdrawalProcessing(anyLong(), anyLong(), anyLong());
    }

    @Test
    void refusesDifferentActorRecordingResultForTheExecutor() {
        AuthorWithdrawalReviewDO withdrawal = withdrawal("PROCESSING", 5L);
        setActor(withdrawal, "setApprovedBy", 88L);
        setActor(withdrawal, "setExecutedBy", 99L);
        when(dao.getWithdrawal(10L)).thenReturn(withdrawal);

        assertThatThrownBy(() -> service.markPaid(10L, 5L, "BANK-REF-001", 100L))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("người thực hiện");

        verify(dao, never()).requestWithdrawalSettlement(anyLong(), anyLong(), anyLong(), anyString());
    }

    @Test
    void refusesInvalidTaxWithholding() {
        AuthorWithdrawalReviewDO withdrawal = withdrawal("PENDING_REVIEW", 0L);
        withdrawal.setGrossAmountVnd(2_000L);
        when(dao.getWithdrawal(10L)).thenReturn(withdrawal);

        assertThatThrownBy(() -> service.approveWithdrawal(10L, 0L, 2_001L, 99L))
            .isInstanceOf(IllegalArgumentException.class);

        verify(dao, never()).approveWithdrawal(anyLong(), anyLong(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void decryptsPayoutSnapshotAndAuditsPiiAccess() throws Exception {
        AuthorWithdrawalReviewDO withdrawal = withdrawal("APPROVED", 1L);
        withdrawal.setBankAccountCiphertext(encrypt("0123456789"));
        withdrawal.setBankAccountNameCiphertext(encrypt("NGUYEN VAN A"));
        when(dao.getWithdrawal(10L)).thenReturn(withdrawal);

        AuthorWithdrawalReviewDO detail = service.getWithdrawalPayoutDetail(10L, 99L);

        assertThat(detail.getBankAccount()).isEqualTo("0123456789");
        assertThat(detail.getBankAccountName()).isEqualTo("NGUYEN VAN A");
        verify(dao).insertWithdrawalAudit(withdrawal, "VIEWED_PAYOUT_PII", "APPROVED", 99L,
            "Quản trị viên xem snapshot tài khoản nhận tiền");
    }

    private AuthorKycReviewDO kyc() {
        AuthorKycReviewDO profile = new AuthorKycReviewDO();
        profile.setId(1L);
        profile.setAuthorId(2L);
        profile.setUserId(3L);
        profile.setStatus("PENDING");
        profile.setSubmissionVersion(2);
        return profile;
    }

    private AuthorWithdrawalReviewDO withdrawal(String status, long version) {
        AuthorWithdrawalReviewDO row = new AuthorWithdrawalReviewDO();
        row.setId(10L);
        row.setWithdrawalNo("WD-1");
        row.setStatus(status);
        row.setVersion(version);
        return row;
    }

    private void setActor(AuthorWithdrawalReviewDO withdrawal, String setter, long actorId) {
        try {
            Method method = AuthorWithdrawalReviewDO.class.getMethod(setter, Long.class);
            method.invoke(withdrawal, actorId);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError("Read model chưa lưu actor riêng cho từng phase payout", exception);
        }
    }

    private String encrypt(String plaintext) throws Exception {
        byte[] key = new byte[32];
        byte[] iv = new byte[12];
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
        cipher.updateAAD("v1".getBytes(StandardCharsets.US_ASCII));
        byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
        byte[] payload = ByteBuffer.allocate(iv.length + ciphertext.length).put(iv).put(ciphertext).array();
        return "v1:" + Base64.getEncoder().encodeToString(payload);
    }
}
