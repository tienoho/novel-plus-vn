package com.java2nb.novel.service.gift;

import com.java2nb.novel.mapper.GiftCodeMapper;
import com.java2nb.novel.service.entitlement.ReadingTicketPostResult;
import com.java2nb.novel.service.entitlement.ReadingTicketService;
import com.java2nb.novel.service.impl.GiftCodeServiceImpl;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import com.java2nb.novel.service.wallet.WalletPostResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;
import java.util.HexFormat;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.eq;

class GiftCodeServiceImplTest {
    private GiftCodeMapper mapper;
    private WalletLedgerService walletService;
    private ReadingTicketService ticketService;
    private GiftCodeProperties properties;
    private GiftCodeServiceImpl service;
    private Date now;

    @BeforeEach
    void setUp() {
        mapper = mock(GiftCodeMapper.class);
        walletService = mock(WalletLedgerService.class);
        ticketService = mock(ReadingTicketService.class);
        properties = new GiftCodeProperties();
        properties.setEnabled(true);
        properties.setHmacSecret("gift-test-secret-at-least-32-characters");
        properties.setPolicyVersion("v1");
        service = new GiftCodeServiceImpl(mapper, walletService, ticketService, properties);
        now = Date.from(Instant.parse("2027-01-01T00:00:00Z"));
    }

    @Test
    void readingTicketGiftUsesTicketLedgerAndCreatesImmutableReceipt() {
        GiftCodeClaimRow claim = claim("READING_TICKET");
        when(mapper.lockClaimByHashes(any())).thenReturn(claim);
        when(mapper.countCampaignUserRedemptions(7L, 11L)).thenReturn(0);
        when(ticketService.grant(any())).thenReturn(ReadingTicketPostResult.POSTED);
        when(mapper.insertTicketRedemption(any(), anyString())).thenReturn(1);
        when(mapper.incrementCodeRedemption(17L, 3L)).thenReturn(1);
        when(mapper.incrementCampaignRedemption(7L, 100L)).thenReturn(1);
        when(mapper.selectRedemptionByCodeUserCurrent(17L, 11L))
            .thenReturn(redemption("READING_TICKET"));

        GiftRedemptionResult result = service.redeem(
            new GiftRedeemCommand(11L, "ABCD-EFGH-JKLM-NPQR", "request_0001", now));

        assertThat(result.status()).isEqualTo(GiftRedemptionStatus.POSTED);
        assertThat(result.rewardType()).isEqualTo("READING_TICKET");
        verify(ticketService).grant(any());
        verify(walletService, never()).creditReaderReward(anyLong(), anyLong(), anyString(),
            anyString(), anyString());
    }

    @Test
    void xuGiftUsesDoubleEntryWalletLedger() {
        GiftCodeClaimRow claim = claim("XU");
        when(mapper.lockClaimByHashes(any())).thenReturn(claim);
        when(mapper.countCampaignUserRedemptions(7L, 11L)).thenReturn(0);
        when(walletService.creditReaderReward(anyLong(), anyLong(), anyString(), anyString(),
            anyString())).thenReturn(WalletPostResult.POSTED);
        when(mapper.insertWalletRedemption(any(), anyString())).thenReturn(1);
        when(mapper.incrementCodeRedemption(17L, 3L)).thenReturn(1);
        when(mapper.incrementCampaignRedemption(7L, 100L)).thenReturn(1);
        when(mapper.selectRedemptionByCodeUserCurrent(17L, 11L)).thenReturn(redemption("XU"));

        GiftRedemptionResult result = service.redeem(
            new GiftRedeemCommand(11L, "ABCD-EFGH-JKLM-NPQR", "request_0002", now));

        assertThat(result.rewardType()).isEqualTo("XU");
        verify(walletService).creditReaderReward(11L, 10L, "17", "GIFT:17:11",
            "Nhận Xu từ mã quà");
        verify(ticketService, never()).grant(any());
    }

    @Test
    void sameUserAndCodeReplayNeverPostsRewardAgain() {
        GiftCodeClaimRow claim = claim("XU");
        GiftRedemptionRow existing = new GiftRedemptionRow();
        existing.setId(31L);
        existing.setRewardType("XU");
        existing.setRewardAmount(10L);
        when(mapper.lockClaimByHashes(any())).thenReturn(claim);
        when(mapper.selectRedemptionByCodeUser(17L, 11L)).thenReturn(existing);

        GiftRedemptionResult result = service.redeem(
            new GiftRedeemCommand(11L, "ABCD-EFGH-JKLM-NPQR", "request_0003", now));

        assertThat(result.status()).isEqualTo(GiftRedemptionStatus.ALREADY_REDEEMED);
        verify(walletService, never()).creditReaderReward(anyLong(), anyLong(), anyString(),
            anyString(), anyString());
        verify(ticketService, never()).grant(any());
    }

    @Test
    void reusedClientRequestForDifferentCodeBecomesDomainRejection() {
        GiftCodeClaimRow claim = claim("XU");
        when(mapper.lockClaimByHashes(any())).thenReturn(claim);
        when(mapper.countCampaignUserRedemptions(7L, 11L)).thenReturn(0);
        when(walletService.creditReaderReward(anyLong(), anyLong(), anyString(), anyString(),
            anyString())).thenReturn(WalletPostResult.POSTED);
        when(mapper.insertWalletRedemption(any(), anyString()))
            .thenThrow(new org.springframework.dao.DuplicateKeyException("request collision"));
        when(mapper.selectRedemptionByUserRequestCurrent(11L, "request_0004"))
            .thenReturn(redemption("XU"));

        assertThatThrownBy(() -> service.redeem(
            new GiftRedeemCommand(11L, "ABCD-EFGH-JKLM-NPQR", "request_0004", now)))
            .isInstanceOf(GiftCodeRedeemException.class);
    }

    @Test
    void issuingCodesReturnsPlaintextOnceButPersistsOnlyHmacAndHint() {
        GiftCampaignRow campaign = new GiftCampaignRow();
        campaign.setId(7L);
        campaign.setStatus("DRAFT");
        campaign.setMaxRedemptions(100L);
        when(mapper.selectCampaignById(7L)).thenReturn(campaign);
        when(mapper.insertCode(eq(7L), eq("legacy-v1"), anyString(), anyString(), eq(1L)))
            .thenReturn(1);

        List<String> codes = service.issueCodes(7L, 2, 1L);

        assertThat(codes).hasSize(2).allMatch(code -> code.matches("[A-Z0-9]{4}(-[A-Z0-9]{4}){4}"));
        verify(mapper, org.mockito.Mockito.times(2))
            .insertCode(eq(7L), eq("legacy-v1"), anyString(), anyString(), eq(1L));
    }

    @Test
    @SuppressWarnings("unchecked")
    void rotatedKeyRingStillFindsCodeIssuedWithLegacyKey() throws Exception {
        String legacySecret = "legacy-gift-secret-at-least-32-characters";
        String activeSecret = "active-gift-secret-at-least-32-characters";
        properties.setHmacKeyId("2027-q1");
        properties.setHmacSecret(activeSecret);
        properties.setHmacVerificationKeys("legacy-v1:" + Base64.getEncoder().encodeToString(
            legacySecret.getBytes(StandardCharsets.UTF_8)));
        GiftCodeClaimRow claim = claim("XU");
        when(mapper.lockClaimByHashes(any())).thenReturn(claim);
        when(mapper.selectRedemptionByCodeUser(17L, 11L)).thenReturn(redemption("XU"));

        service.redeem(new GiftRedeemCommand(
            11L, "ABCD-EFGH-JKLM-NPQR", "rotation_0001", now));

        ArgumentCaptor<List<GiftCodeHashCandidate>> captor =
            (ArgumentCaptor<List<GiftCodeHashCandidate>>) (ArgumentCaptor<?>)
                ArgumentCaptor.forClass(List.class);
        verify(mapper).lockClaimByHashes(captor.capture());
        assertThat(captor.getValue()).extracting(GiftCodeHashCandidate::keyId)
            .containsExactly("2027-q1", "legacy-v1");
        assertThat(captor.getValue()).filteredOn(candidate -> "legacy-v1".equals(candidate.keyId()))
            .singleElement().extracting(GiftCodeHashCandidate::codeHash)
            .isEqualTo(hmac("ABCDEFGHJKLMNPQR", legacySecret));
    }

    @Test
    void invalidOrDuplicateVerificationKeysFailClosed() {
        properties.setHmacVerificationKeys("legacy-v1:" + Base64.getEncoder().encodeToString(
            "another-secret-at-least-32-characters".getBytes(StandardCharsets.UTF_8)));

        assertThat(properties.hasValidKeyRing()).isFalse();
        assertThat(properties.isReady()).isFalse();
    }

    @Test
    void revokesOnlyUnusedCodeAtExpectedVersion() {
        GiftCodeRow row = new GiftCodeRow();
        row.setId(17L);
        row.setStatus("REVOKED");
        row.setVersion(4L);
        when(mapper.revokeUnusedCode(17L, 3L)).thenReturn(1);
        when(mapper.selectCodeById(17L)).thenReturn(row);

        GiftCodeRow result = service.revokeCode(17L, 3L);

        assertThat(result.getStatus()).isEqualTo("REVOKED");
        assertThat(result.getVersion()).isEqualTo(4L);
    }

    @Test
    void failedRevokeNeverPretendsSuccess() {
        when(mapper.revokeUnusedCode(17L, 3L)).thenReturn(0);

        assertThatThrownBy(() -> service.revokeCode(17L, 3L))
            .isInstanceOf(IllegalStateException.class);
    }

    private GiftCodeClaimRow claim(String rewardType) {
        GiftCodeClaimRow row = new GiftCodeClaimRow();
        row.setCodeId(17L);
        row.setCampaignId(7L);
        row.setCodeStatus("ACTIVE");
        row.setCampaignStatus("ACTIVE");
        row.setRewardType(rewardType);
        row.setRewardAmount(10L);
        row.setTicketValidityDays(45);
        row.setStartAt(Date.from(now.toInstant().minusSeconds(60)));
        row.setEndAt(Date.from(now.toInstant().plusSeconds(3600)));
        row.setCodeMaxRedemptions(3L);
        row.setCodeRedeemedCount(0L);
        row.setCampaignMaxRedemptions(100L);
        row.setCampaignRedeemedCount(0L);
        row.setMaxPerUser(1);
        return row;
    }

    private GiftRedemptionRow redemption(String rewardType) {
        GiftRedemptionRow row = new GiftRedemptionRow();
        row.setId(31L);
        row.setCampaignId(7L);
        row.setCodeId(17L);
        row.setUserId(11L);
        row.setRewardType(rewardType);
        row.setRewardAmount(10L);
        return row;
    }

    private String hmac(String code, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return HexFormat.of().formatHex(mac.doFinal(code.getBytes(StandardCharsets.UTF_8)));
    }
}
