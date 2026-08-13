package com.java2nb.novel.service.subscription;

import com.java2nb.novel.mapper.ReadingSubscriptionMapper;
import com.java2nb.novel.mapper.ReadingSubscriptionMandateMapper;
import com.java2nb.novel.mapper.ReadingSubscriptionPurchaseMapper;
import com.java2nb.novel.service.entitlement.ReadingTicketPostResult;
import com.java2nb.novel.service.entitlement.ReadingTicketService;
import com.java2nb.novel.service.impl.ReadingSubscriptionServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;

import java.time.Instant;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;

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

class ReadingSubscriptionServiceImplTest {
    private ReadingSubscriptionMapper mapper;
    private ReadingSubscriptionMandateMapper mandateMapper;
    private ReadingSubscriptionPurchaseMapper purchaseMapper;
    private ReadingTicketService ticketService;
    private ReadingSubscriptionServiceImpl service;
    private final Date start = Date.from(Instant.parse("2027-01-01T00:00:00Z"));
    private final Date end = Date.from(Instant.parse("2027-04-01T00:00:00Z"));

    @BeforeEach
    void setUp() {
        mapper = mock(ReadingSubscriptionMapper.class);
        mandateMapper = mock(ReadingSubscriptionMandateMapper.class);
        purchaseMapper = mock(ReadingSubscriptionPurchaseMapper.class);
        ticketService = mock(ReadingTicketService.class);
        service = new ReadingSubscriptionServiceImpl(mapper, mandateMapper, purchaseMapper,
            ticketService);
    }

    @Test
    void activationSnapshotsOnlyActivePlanBenefits() {
        ReadingSubscriptionPlanRow plan = plan();
        ReadingSubscriptionRow created = subscription();
        when(mapper.selectActivePlanByCode("BASIC_MONTHLY")).thenReturn(plan);
        when(mapper.selectSubscriptionBySource("ADMIN", "activation-001"))
            .thenReturn(null, created);
        when(mapper.insertSubscription(any(), any())).thenReturn(1);
        ReadingSubscriptionActivationCommand command = new ReadingSubscriptionActivationCommand(
            11L, "BASIC_MONTHLY", start, end, "ADMIN", "activation-001", "v1");

        assertThat(service.activate(command)).isSameAs(created);

        ArgumentCaptor<ReadingSubscriptionPlanRow> planCaptor =
            ArgumentCaptor.forClass(ReadingSubscriptionPlanRow.class);
        verify(mapper).insertSubscription(planCaptor.capture(), any());
        assertThat(planCaptor.getValue().getTicketsPerPeriod()).isEqualTo(10L);
    }

    @Test
    void activationReplayDoesNotInsertAnotherSubscription() {
        ReadingSubscriptionRow existing = subscription();
        when(mapper.selectSubscriptionBySource("ADMIN", "activation-001")).thenReturn(existing);

        ReadingSubscriptionRow replay = service.activate(new ReadingSubscriptionActivationCommand(
            11L, "BASIC_MONTHLY", start, end, "ADMIN", "activation-001", "v1"));

        assertThat(replay).isSameAs(existing);
        verify(mapper, never()).insertSubscription(any(), any());
    }

    @Test
    void concurrentActivationReplayUsesCurrentReadAfterDuplicateKey() {
        ReadingSubscriptionRow existing = subscription();
        when(mapper.selectSubscriptionBySource("ADMIN", "activation-001")).thenReturn(null);
        when(mapper.selectActivePlanByCode("BASIC_MONTHLY")).thenReturn(plan());
        when(mapper.insertSubscription(any(), any()))
            .thenThrow(new DuplicateKeyException("concurrent source"));
        when(mapper.selectSubscriptionBySourceForUpdate("ADMIN", "activation-001"))
            .thenReturn(existing);

        ReadingSubscriptionRow replay = service.activate(new ReadingSubscriptionActivationCommand(
            11L, "BASIC_MONTHLY", start, end, "ADMIN", "activation-001", "v1"));

        assertThat(replay).isSameAs(existing);
        verify(mapper).selectSubscriptionBySourceForUpdate("ADMIN", "activation-001");
    }

    @Test
    void duePeriodGrantsTicketsAndAdvancesSubscriptionAtomically() {
        ReadingSubscriptionRow row = subscription();
        when(mapper.lockDueSubscription(51L, start)).thenReturn(row);
        when(ticketService.grant(any())).thenReturn(ReadingTicketPostResult.POSTED);
        when(mapper.insertPeriodGrantFromLedger(anyLong(), anyLong(), any(), any(), anyLong(),
            any(), anyString()))
            .thenReturn(1);
        when(mapper.advanceSubscription(51L, 3L, Date.from(Instant.parse("2027-02-01T00:00:00Z")),
            "ACTIVE")).thenReturn(1);

        ReadingSubscriptionGrantResult result = service.grantDuePeriod(
            51L, start, ZoneId.of("UTC"), "v1");

        assertThat(result.status()).isEqualTo(ReadingSubscriptionGrantStatus.POSTED);
        assertThat(result.ticketAmount()).isEqualTo(10L);
        assertThat(result.periodEnd()).isEqualTo(Date.from(Instant.parse("2027-02-01T00:00:00Z")));
        verify(ticketService).grant(any());
    }

    @Test
    void readModelsAlwaysApplyUserOwnershipAndLimit() {
        ReadingSubscriptionRow current = subscription();
        ReadingSubscriptionPeriodGrantRow period = new ReadingSubscriptionPeriodGrantRow();
        when(mapper.selectCurrentSubscriptionByUserId(11L)).thenReturn(current);
        when(mapper.selectPeriodGrantsByUser(11L, 51L, 25)).thenReturn(List.of(period));

        assertThat(service.getCurrentSubscription(11L)).isSameAs(current);
        assertThat(service.listPeriodGrants(11L, 51L, 25)).containsExactly(period);
        verify(mapper).selectPeriodGrantsByUser(11L, 51L, 25);
    }

    @Test
    void planLifecycleUsesOptimisticVersionAndNeverDeletes() {
        ReadingSubscriptionPlanCommand draft = new ReadingSubscriptionPlanCommand(
            "basic_monthly", "Gói cơ bản", 49_000, 10, 1, 45);
        ReadingSubscriptionPlanRow created = plan();
        when(mapper.insertPlan(draft)).thenReturn(1);
        when(mapper.selectPlanByCode("BASIC_MONTHLY")).thenReturn(created);

        assertThat(service.createPlan(draft)).isSameAs(created);

        when(mapper.selectPlanById(7L)).thenReturn(created);
        when(mapper.updatePlan(7L, 3L, draft)).thenReturn(0);
        assertThatThrownBy(() -> service.updatePlan(7L, 3L, draft))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("đồng thời");
    }

    @Test
    void retiredPlanCannotBeReactivated() {
        ReadingSubscriptionPlanRow retired = plan();
        retired.setStatus("RETIRED");
        when(mapper.selectPlanById(7L)).thenReturn(retired);

        assertThatThrownBy(() -> service.changePlanStatus(7L, 3L, "ACTIVE"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("RETIRED");
    }

    @Test
    void paidPurchaseActivationUsesImmutableSnapshotAndIsIdempotent() {
        ReadingSubscriptionPurchaseActivationCommand command =
            new ReadingSubscriptionPurchaseActivationCommand(
                11L, 7L, "BASIC_MONTHLY", 10L, 1, 45,
                start, end, "123456", "v1", 1, 49_000L, 500L,
                false, null, null, 1);
        ReadingSubscriptionRow created = subscription();
        created.setSourceType("PAYMENT");
        created.setSourceRef("123456");
        when(mapper.selectSubscriptionBySource("PAYMENT", "123456"))
            .thenReturn(null, created);
        when(mapper.insertPurchasedSubscription(command)).thenReturn(1);

        assertThat(service.activatePurchase(command)).isSameAs(created);
        verify(mapper).insertPurchasedSubscription(command);
    }

    @Test
    void paidPurchaseConflictReturnsNullForReviewInsteadOfOverwritingOpenSubscription() {
        ReadingSubscriptionPurchaseActivationCommand command =
            new ReadingSubscriptionPurchaseActivationCommand(
                11L, 7L, "BASIC_MONTHLY", 10L, 1, 45,
                start, end, "123456", "v1", 1, 49_000L, 500L,
                false, null, null, 1);
        when(mapper.insertPurchasedSubscription(command)).thenReturn(0);

        assertThat(service.activatePurchase(command)).isNull();
    }

    @Test
    void retryPaidReviewActivatesFromImmutablePurchaseSnapshotAndWritesAudit() {
        ReadingSubscriptionPurchaseRow purchase = reviewPurchase();
        ReadingSubscriptionRow activated = subscription();
        activated.setSourceType("PAYMENT");
        activated.setSourceRef("123456");
        activated.setEndAt(Date.from(Instant.parse("2027-02-01T00:00:00Z")));
        when(purchaseMapper.selectByIdForUpdate(801L)).thenReturn(purchase);
        when(mapper.selectSubscriptionBySource("PAYMENT", "123456"))
            .thenReturn(null, activated);
        when(mapper.insertPurchasedSubscription(any())).thenReturn(1);
        when(purchaseMapper.markReviewActivated(anyLong(), anyLong(), anyLong(), any()))
            .thenReturn(1);
        when(purchaseMapper.insertReviewAudit(any(), anyString(), anyString(), anyLong(),
            anyString(), anyLong(), any())).thenReturn(1);

        assertThat(service.retryPurchaseActivation(801L, 2L, 91L,
            "Đã xác minh giao dịch ngân hàng"))
            .isEqualTo(ReadingSubscriptionPurchaseReviewResult.ACTIVATED);

        verify(purchaseMapper).markReviewActivated(eq(801L), eq(2L), eq(51L), any());
        verify(purchaseMapper).insertReviewAudit(eq(purchase), eq("RETRY_ACTIVATED"),
            eq("ACTIVATED"), eq(91L), eq("Đã xác minh giao dịch ngân hàng"), eq(3L), any());
    }

    @Test
    void retryPaidReviewKeepsQueueWhenAnotherSubscriptionIsStillOpen() {
        ReadingSubscriptionPurchaseRow purchase = reviewPurchase();
        when(purchaseMapper.selectByIdForUpdate(801L)).thenReturn(purchase);
        when(mapper.insertPurchasedSubscription(any())).thenReturn(0);
        when(purchaseMapper.insertReviewAudit(any(), anyString(), anyString(), anyLong(),
            anyString(), anyLong(), any())).thenReturn(1);

        assertThat(service.retryPurchaseActivation(801L, 2L, 91L,
            "Thuê bao cũ vẫn đang hoạt động"))
            .isEqualTo(ReadingSubscriptionPurchaseReviewResult.BLOCKED_BY_OPEN_SUBSCRIPTION);

        verify(purchaseMapper, never()).markReviewActivated(anyLong(), anyLong(), anyLong(), any());
        verify(purchaseMapper).insertReviewAudit(eq(purchase), eq("RETRY_BLOCKED"),
            eq("PAID_REVIEW"), eq(91L), eq("Thuê bao cũ vẫn đang hoạt động"), eq(2L), any());
    }

    @Test
    void refundTransitionRequiresPaidReviewVersionAndWritesAudit() {
        ReadingSubscriptionPurchaseRow purchase = reviewPurchase();
        when(purchaseMapper.selectByIdForUpdate(801L)).thenReturn(purchase);
        when(purchaseMapper.markRefundPending(eq(801L), eq(2L), any())).thenReturn(1);
        when(purchaseMapper.insertReviewAudit(any(), anyString(), anyString(), anyLong(),
            anyString(), anyLong(), any())).thenReturn(1);

        assertThat(service.sendPurchaseToRefund(801L, 2L, 91L,
            "Khách hàng yêu cầu hoàn tiền"))
            .isEqualTo(ReadingSubscriptionPurchaseReviewResult.REFUND_PENDING);
        verify(purchaseMapper).insertReviewAudit(eq(purchase), eq("REFUND_REQUESTED"),
            eq("REFUND_PENDING"), eq(91L), eq("Khách hàng yêu cầu hoàn tiền"), eq(3L), any());
    }

    @Test
    void cancelAtPeriodEndQueuesActiveVnpayMandateRevocationAtomically() {
        ReadingSubscriptionRow current = subscription();
        ReadingSubscriptionRow cancelled = subscription();
        cancelled.setStatus("CANCEL_AT_PERIOD_END");
        cancelled.setVersion(4L);
        when(mapper.selectSubscriptionForUserForUpdate(51L, 11L)).thenReturn(current);
        when(mandateMapper.requestRevocation(eq(11L), any())).thenReturn(1);
        when(mapper.cancelAtPeriodEnd(eq(51L), eq(11L), eq(3L), any())).thenReturn(1);
        when(mapper.selectCurrentSubscriptionByUserId(11L)).thenReturn(cancelled);

        assertThat(service.cancelAtPeriodEnd(11L, 51L, 3L)).isSameAs(cancelled);

        verify(mandateMapper).requestRevocation(eq(11L), any());
        verify(mapper).cancelAtPeriodEnd(eq(51L), eq(11L), eq(3L), any());
    }

    @Test
    void adminMandateRetryRequiresExpiredLeaseAndWritesImmutableAuditInput() {
        ReadingSubscriptionMandateRow mandate = mandate();
        Date now = Date.from(Instant.parse("2027-02-01T00:05:00Z"));
        when(mandateMapper.selectByIdForUpdate(71L)).thenReturn(mandate);
        when(mandateMapper.adminScheduleRevocationRetry(71L, 4L, now)).thenReturn(1);
        when(mandateMapper.insertMandateAdminAudit(71L, 11L, 91L,
            "RETRY_SCHEDULED", "REVOKE_PENDING", "REVOKE_PENDING",
            "Đã xác minh provider có thể nhận retry", now)).thenReturn(1);

        assertThat(service.adminScheduleMandateRevocationRetry(71L, 4L, 91L,
            "Đã xác minh provider có thể nhận retry", now))
            .isEqualTo(ReadingSubscriptionMandateAdminResult.RETRY_SCHEDULED);
        verify(mandateMapper).insertMandateAdminAudit(71L, 11L, 91L,
            "RETRY_SCHEDULED", "REVOKE_PENDING", "REVOKE_PENDING",
            "Đã xác minh provider có thể nhận retry", now);
    }

    @Test
    void adminMandateRetryCannotStealActiveWorkerLease() {
        ReadingSubscriptionMandateRow mandate = mandate();
        mandate.setRevokeLeaseUntil(Date.from(Instant.parse("2027-02-01T00:10:00Z")));
        Date now = Date.from(Instant.parse("2027-02-01T00:05:00Z"));
        when(mandateMapper.selectByIdForUpdate(71L)).thenReturn(mandate);

        assertThatThrownBy(() -> service.adminScheduleMandateRevocationRetry(
            71L, 4L, 91L, "Yêu cầu retry khi worker đang chạy", now))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("worker");
        verify(mandateMapper, never()).adminScheduleRevocationRetry(anyLong(), anyLong(), any());
    }

    private ReadingSubscriptionPlanRow plan() {
        ReadingSubscriptionPlanRow row = new ReadingSubscriptionPlanRow();
        row.setId(7L);
        row.setPlanCode("BASIC_MONTHLY");
        row.setPlanVersion(1L);
        row.setPriceVnd(49_000L);
        row.setPriceXu(500L);
        row.setTicketsPerPeriod(10L);
        row.setPeriodMonths(1);
        row.setTicketValidityDays(45);
        row.setStatus("ACTIVE");
        row.setVersion(3L);
        return row;
    }

    private ReadingSubscriptionRow subscription() {
        ReadingSubscriptionRow row = new ReadingSubscriptionRow();
        row.setId(51L);
        row.setUserId(11L);
        row.setPlanId(7L);
        row.setPlanCodeSnapshot("BASIC_MONTHLY");
        row.setPlanVersionSnapshot(1L);
        row.setPriceVndSnapshot(49_000L);
        row.setPriceXuSnapshot(500L);
        row.setAcceptedPlanVersion(1L);
        row.setTicketsPerPeriodSnapshot(10L);
        row.setPeriodMonthsSnapshot(1);
        row.setTicketValidityDaysSnapshot(45);
        row.setStartAt(start);
        row.setNextGrantAt(start);
        row.setEndAt(end);
        row.setAutoRenew(false);
        row.setStatus("ACTIVE");
        row.setSourceType("ADMIN");
        row.setSourceRef("activation-001");
        row.setPolicyVersion("v1");
        row.setVersion(3L);
        return row;
    }

    private ReadingSubscriptionPurchaseRow reviewPurchase() {
        ReadingSubscriptionPurchaseRow row = new ReadingSubscriptionPurchaseRow();
        row.setId(801L);
        row.setOutTradeNo(123456L);
        row.setUserId(11L);
        row.setPlanId(7L);
        row.setPlanCodeSnapshot("BASIC_MONTHLY");
        row.setPlanVersionSnapshot(1L);
        row.setPlanNameSnapshot("Gói cơ bản");
        row.setPriceVndSnapshot(49_000L);
        row.setPriceXuSnapshot(500L);
        row.setAutoRenew(false);
        row.setAcceptedPlanVersion(1L);
        row.setTicketsPerPeriodSnapshot(10L);
        row.setPeriodMonthsSnapshot(1);
        row.setTicketValidityDaysSnapshot(45);
        row.setPayChannel((byte) 4);
        row.setPolicyVersion("v1");
        row.setZoneId("UTC");
        row.setStatus("PAID_REVIEW");
        row.setSettledAt(start);
        row.setVersion(2L);
        return row;
    }

    private ReadingSubscriptionMandateRow mandate() {
        ReadingSubscriptionMandateRow row = new ReadingSubscriptionMandateRow();
        row.setId(71L);
        row.setUserId(11L);
        row.setStatus("REVOKE_PENDING");
        row.setVersion(4L);
        return row;
    }
}
