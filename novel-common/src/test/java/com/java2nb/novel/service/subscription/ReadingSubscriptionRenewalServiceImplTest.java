package com.java2nb.novel.service.subscription;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.java2nb.novel.mapper.ReadingSubscriptionMapper;
import com.java2nb.novel.mapper.ReadingSubscriptionRenewalMapper;
import com.java2nb.novel.service.impl.ReadingSubscriptionRenewalServiceImpl;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import com.java2nb.novel.service.wallet.WalletPostResult;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ReadingSubscriptionRenewalServiceImplTest {
    private ReadingSubscriptionRenewalMapper renewalMapper;
    private ReadingSubscriptionMapper subscriptionMapper;
    private WalletLedgerService walletLedgerService;
    private ReadingSubscriptionRenewalServiceImpl service;
    private final Date now = Date.from(Instant.parse("2027-02-01T00:00:00Z"));

    @BeforeEach
    void setUp() {
        renewalMapper = mock(ReadingSubscriptionRenewalMapper.class);
        subscriptionMapper = mock(ReadingSubscriptionMapper.class);
        walletLedgerService = mock(WalletLedgerService.class);
        service = new ReadingSubscriptionRenewalServiceImpl(
            renewalMapper, subscriptionMapper, walletLedgerService);
    }

    @Test
    void preparesOneCycleFromAcceptedSnapshot() {
        ReadingSubscriptionRow subscription = subscription("WALLET_XU", null);
        ReadingSubscriptionPlanRow plan = plan(1L, null);
        when(renewalMapper.lockDueSubscription(51L, now)).thenReturn(subscription);
        when(subscriptionMapper.selectPlanById(7L)).thenReturn(plan);
        when(renewalMapper.insertCycle(eq(subscription), eq(now), any(), any(),
            eq("SUBSCRIPTION_RENEWAL:51:1801440000000"), eq(now))).thenReturn(1);

        assertThat(service.prepareCycle(51L, now, ZoneId.of("UTC")))
            .isEqualTo(ReadingSubscriptionRenewalResult.CYCLE_CREATED);
    }

    @Test
    void stopsAutoRenewWhenEffectivePriceWasNotAccepted() {
        ReadingSubscriptionRow subscription = subscription("WALLET_XU", null);
        ReadingSubscriptionPlanRow plan = plan(2L, new Date(now.getTime() - 1));
        when(renewalMapper.lockDueSubscription(51L, now)).thenReturn(subscription);
        when(subscriptionMapper.selectPlanById(7L)).thenReturn(plan);
        when(renewalMapper.markPriceConsentRequired(51L, 3L, now)).thenReturn(1);

        assertThat(service.prepareCycle(51L, now, ZoneId.of("UTC")))
            .isEqualTo(ReadingSubscriptionRenewalResult.PRICE_CONSENT_REQUIRED);
        verify(renewalMapper, never()).insertCycle(any(), any(), any(), any(), anyString(), any());
    }

    @Test
    void settlesWalletCycleWithOneLedgerIdempotencyKey() {
        ReadingSubscriptionRenewalCycleRow cycle = cycle();
        ReadingSubscriptionRow subscription = subscription("WALLET_XU", null);
        when(renewalMapper.lockCycle(81L)).thenReturn(cycle);
        when(renewalMapper.lockSubscription(51L)).thenReturn(subscription);
        when(renewalMapper.insertAttempt(eq(81L), eq(1), eq("WALLET_XU"),
            eq("STARTED"), anyString(), eq(now))).thenReturn(1);
        when(walletLedgerService.chargeReaderSubscription(
            11L, 500L, "81", "SUBSCRIPTION_RENEWAL:51:1801440000000:WALLET_XU"))
            .thenReturn(WalletPostResult.POSTED);
        when(renewalMapper.markAttemptSettled(anyLong(), anyInt(), anyString(), any()))
            .thenReturn(1);
        when(renewalMapper.markCycleSettled(anyLong(), anyLong(), anyString(), anyString(), any()))
            .thenReturn(1);
        when(renewalMapper.advanceSubscription(cycle, 3L)).thenReturn(1);

        assertThat(service.processCycle(81L, now))
            .isEqualTo(ReadingSubscriptionRenewalResult.SETTLED);
        verify(walletLedgerService).chargeReaderSubscription(
            11L, 500L, "81", "SUBSCRIPTION_RENEWAL:51:1801440000000:WALLET_XU");
    }

    @Test
    void recordsWalletFailureAndRetriesAtTwentyFourHours() {
        ReadingSubscriptionRenewalCycleRow cycle = cycle();
        ReadingSubscriptionRow subscription = subscription("WALLET_XU", null);
        when(renewalMapper.lockCycle(81L)).thenReturn(cycle);
        when(renewalMapper.lockSubscription(51L)).thenReturn(subscription);
        when(renewalMapper.insertAttempt(anyLong(), anyInt(), anyString(), anyString(),
            anyString(), any())).thenReturn(1);
        when(renewalMapper.markAttemptFailed(anyLong(), anyInt(), anyString(), any()))
            .thenReturn(1);
        when(renewalMapper.scheduleRetry(eq(81L), eq(0L), eq(1),
            eq(new Date(now.getTime() + 24L * 60 * 60 * 1000)))).thenReturn(1);
        when(renewalMapper.markSubscriptionPastDue(51L, 3L)).thenReturn(1);

        assertThat(service.recordWalletFailure(81L, now))
            .isEqualTo(ReadingSubscriptionRenewalResult.RETRY_SCHEDULED);
    }

    @Test
    void providerSourceStaysPendingWithoutTryingFallback() {
        ReadingSubscriptionRenewalCycleRow cycle = cycle();
        ReadingSubscriptionRow subscription = subscription("VNPAY_RECURRING", "WALLET_XU");
        when(renewalMapper.lockCycle(81L)).thenReturn(cycle);
        when(renewalMapper.lockSubscription(51L)).thenReturn(subscription);
        when(renewalMapper.insertProviderAttempt(eq(81L), eq(1), eq("NPR81A1"),
            org.mockito.ArgumentMatchers.anyString(), eq(now))).thenReturn(1);
        when(renewalMapper.markProviderProcessing(81L, 0L, 1, now)).thenReturn(1);
        when(renewalMapper.markSubscriptionPastDue(51L, 3L)).thenReturn(1);

        assertThat(service.processCycle(81L, now))
            .isEqualTo(ReadingSubscriptionRenewalResult.PROVIDER_CLAIMED);
        verify(walletLedgerService, never()).chargeReaderSubscription(
            anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void settlesOnlyTheClaimedProviderAttempt() {
        ReadingSubscriptionRenewalCycleRow cycle = claimedCycle();
        ReadingSubscriptionRow subscription = subscription("VNPAY_RECURRING", "WALLET_XU");
        when(renewalMapper.lockCycle(81L)).thenReturn(cycle);
        when(renewalMapper.lockSubscription(51L)).thenReturn(subscription);
        when(renewalMapper.markProviderAttemptSettled(81L, 1,
            "666821925535879168", now)).thenReturn(1);
        when(renewalMapper.markProviderCycleSettled(81L, 1L,
            "666821925535879168", now)).thenReturn(1);
        when(renewalMapper.advanceSubscription(cycle, 3L)).thenReturn(1);

        assertThat(service.settleProviderCharge(81L, 1, "666821925535879168", now))
            .isEqualTo(ReadingSubscriptionRenewalResult.SETTLED);
    }

    @Test
    void unknownProviderResultCannotReachFallback() {
        ReadingSubscriptionRenewalCycleRow cycle = claimedCycle();
        when(renewalMapper.lockCycle(81L)).thenReturn(cycle);
        when(renewalMapper.markProviderAttemptUnknown(81L, 1, "UNAVAILABLE", now)).thenReturn(1);
        when(renewalMapper.markProviderPending(81L, 1L, 1, now)).thenReturn(1);

        assertThat(service.recordProviderPending(81L, 1, "UNAVAILABLE", now))
            .isEqualTo(ReadingSubscriptionRenewalResult.PROVIDER_PENDING);
        verify(renewalMapper, never()).scheduleProviderRetry(anyLong(), anyLong(), any());
    }

    @Test
    void claimsOnePendingProviderQueryWithLease() {
        Date nextQueryAt = new Date(now.getTime() + 300_000L);
        ReadingSubscriptionProviderQuery query = new ReadingSubscriptionProviderQuery(
            81L, 1, "NPR81A1", 49_000L, now);
        when(renewalMapper.claimProviderQuery(81L, now, nextQueryAt)).thenReturn(1);
        when(renewalMapper.selectProviderQuery(81L)).thenReturn(query);

        assertThat(service.claimProviderQuery(81L, now, nextQueryAt)).isEqualTo(query);
    }

    @Test
    void settlesUnknownProviderAttemptAfterSignedQuery() {
        ReadingSubscriptionRenewalCycleRow cycle = pendingCycle();
        ReadingSubscriptionRow subscription = subscription("VNPAY_RECURRING", "WALLET_XU");
        when(renewalMapper.lockCycle(81L)).thenReturn(cycle);
        when(renewalMapper.lockSubscription(51L)).thenReturn(subscription);
        when(renewalMapper.markUnknownProviderAttemptSettled(
            81L, 1, "666821925535879168", now)).thenReturn(1);
        when(renewalMapper.markPendingProviderCycleSettled(
            81L, 2L, "666821925535879168", now)).thenReturn(1);
        when(renewalMapper.advanceSubscription(cycle, 3L)).thenReturn(1);

        assertThat(service.settleProviderQuery(81L, 1, "666821925535879168", now))
            .isEqualTo(ReadingSubscriptionRenewalResult.SETTLED);
    }

    @Test
    void finalProviderQueryFailureSchedulesRetryBeforeFallback() {
        ReadingSubscriptionRenewalCycleRow cycle = pendingCycle();
        ReadingSubscriptionRow subscription = subscription("VNPAY_RECURRING", "WALLET_XU");
        Date retryAt = new Date(now.getTime() + 24L * 60 * 60 * 1000);
        when(renewalMapper.lockCycle(81L)).thenReturn(cycle);
        when(renewalMapper.lockSubscription(51L)).thenReturn(subscription);
        when(renewalMapper.markUnknownProviderAttemptFailed(81L, 1, "02", now)).thenReturn(1);
        when(renewalMapper.scheduleProviderQueryRetry(81L, 2L, retryAt)).thenReturn(1);

        assertThat(service.recordProviderQueryFailure(81L, 1, "02", now))
            .isEqualTo(ReadingSubscriptionRenewalResult.RETRY_SCHEDULED);
        verify(walletLedgerService, never()).chargeReaderSubscription(
            anyLong(), anyLong(), anyString(), anyString());
    }

    @Test
    void adminRetryRequiresRetryWaitAndWritesAudit() {
        ReadingSubscriptionRenewalCycleRow cycle = cycle();
        cycle.setStatus("RETRY_WAIT");
        cycle.setVersion(2L);
        when(renewalMapper.lockCycle(81L)).thenReturn(cycle);
        when(renewalMapper.adminScheduleRetry(81L, 2L, now)).thenReturn(1);
        when(renewalMapper.insertRenewalAdminAudit(81L, 11L, 99L,
            "RETRY_SCHEDULED", "RETRY_WAIT", "RETRY_WAIT",
            "Đã xác minh lỗi cuối cùng", now)).thenReturn(1);

        assertThat(service.adminScheduleRetry(81L, 2L, 99L,
            "Đã xác minh lỗi cuối cùng", now))
            .isEqualTo(ReadingSubscriptionRenewalResult.RETRY_SCHEDULED);
        verify(renewalMapper).insertRenewalAdminAudit(81L, 11L, 99L,
            "RETRY_SCHEDULED", "RETRY_WAIT", "RETRY_WAIT",
            "Đã xác minh lỗi cuối cùng", now);
    }

    @Test
    void adminQueueRejectsUnlistedStatusBeforeSql() {
        assertThatThrownBy(() -> service.listRenewalQueue("SETTLED' OR 1=1", 50))
            .isInstanceOf(IllegalArgumentException.class);
        verify(renewalMapper, never()).selectRenewalQueue(anyString(), anyInt());
    }

    private ReadingSubscriptionPlanRow plan(long planVersion, Date effectiveAt) {
        ReadingSubscriptionPlanRow row = new ReadingSubscriptionPlanRow();
        row.setId(7L);
        row.setPlanVersion(planVersion);
        row.setPriceVnd(49_000L);
        row.setPriceXu(500L);
        row.setPriceEffectiveAt(effectiveAt);
        row.setStatus("ACTIVE");
        return row;
    }

    private ReadingSubscriptionRow subscription(String primary, String fallback) {
        ReadingSubscriptionRow row = new ReadingSubscriptionRow();
        row.setId(51L);
        row.setUserId(11L);
        row.setPlanId(7L);
        row.setPlanVersionSnapshot(1L);
        row.setAcceptedPlanVersion(1L);
        row.setPriceVndSnapshot(49_000L);
        row.setPriceXuSnapshot(500L);
        row.setPeriodMonthsSnapshot(1);
        row.setCurrentPeriodEnd(now);
        row.setAutoRenew(true);
        row.setPrimaryFundingSource(primary);
        row.setFallbackFundingSource(fallback);
        row.setStatus("ACTIVE");
        row.setVersion(3L);
        return row;
    }

    private ReadingSubscriptionRenewalCycleRow cycle() {
        ReadingSubscriptionRenewalCycleRow row = new ReadingSubscriptionRenewalCycleRow();
        row.setId(81L);
        row.setSubscriptionId(51L);
        row.setUserId(11L);
        row.setPeriodStart(now);
        row.setPeriodEnd(Date.from(Instant.parse("2027-03-01T00:00:00Z")));
        row.setGraceEndAt(Date.from(Instant.parse("2027-02-08T00:00:00Z")));
        row.setPriceVndSnapshot(49_000L);
        row.setPriceXuSnapshot(500L);
        row.setStatus("DUE");
        row.setAttemptCount(0);
        row.setNextAttemptAt(now);
        row.setIdempotencyKey("SUBSCRIPTION_RENEWAL:51:1801440000000");
        row.setVersion(0L);
        return row;
    }

    private ReadingSubscriptionRenewalCycleRow claimedCycle() {
        ReadingSubscriptionRenewalCycleRow row = cycle();
        row.setStatus("PROCESSING");
        row.setAttemptCount(1);
        row.setVersion(1L);
        return row;
    }

    private ReadingSubscriptionRenewalCycleRow pendingCycle() {
        ReadingSubscriptionRenewalCycleRow row = cycle();
        row.setStatus("PROVIDER_PENDING");
        row.setAttemptCount(1);
        row.setVersion(2L);
        return row;
    }
}
