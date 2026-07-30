package com.java2nb.novel.service.impl;

import com.java2nb.novel.entity.OrderPay;
import com.java2nb.novel.mapper.OrderPayMapper;
import com.java2nb.novel.mapper.ReadingSubscriptionMapper;
import com.java2nb.novel.mapper.ReadingSubscriptionPurchaseMapper;
import com.java2nb.novel.core.config.ReaderEntitlementProperties;
import com.java2nb.novel.service.PayOrderCreation;
import com.java2nb.novel.service.PayOrderSnapshot;
import com.java2nb.novel.service.PayOrderState;
import com.java2nb.novel.service.PayOrderUpdateResult;
import com.java2nb.novel.service.ReadingSubscriptionCheckoutCreation;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import com.java2nb.novel.service.wallet.WalletPostResult;
import com.java2nb.novel.service.gamification.GamificationEventService;
import com.java2nb.novel.service.subscription.ReadingSubscriptionService;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPlanRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPurchaseActivationCommand;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPurchaseRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;
import org.mybatis.dynamic.sql.update.render.UpdateStatementProvider;
import org.springframework.dao.DuplicateKeyException;

import java.util.Optional;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class OrderServiceImplTest {

    private OrderPayMapper orderPayMapper;
    private WalletLedgerService walletLedgerService;
    private GamificationEventService gamificationEventService;
    private ReadingSubscriptionMapper readingSubscriptionMapper;
    private ReadingSubscriptionPurchaseMapper purchaseMapper;
    private ReadingSubscriptionService readingSubscriptionService;
    private ReaderEntitlementProperties entitlementProperties;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        orderPayMapper = mock(OrderPayMapper.class);
        walletLedgerService = mock(WalletLedgerService.class);
        gamificationEventService = mock(GamificationEventService.class);
        readingSubscriptionMapper = mock(ReadingSubscriptionMapper.class);
        purchaseMapper = mock(ReadingSubscriptionPurchaseMapper.class);
        readingSubscriptionService = mock(ReadingSubscriptionService.class);
        entitlementProperties = new ReaderEntitlementProperties();
        service = new OrderServiceImpl(orderPayMapper, walletLedgerService, gamificationEventService,
            readingSubscriptionMapper, purchaseMapper, readingSubscriptionService,
            entitlementProperties);
        when(orderPayMapper.insertSelective(any(OrderPay.class))).thenReturn(1);
        when(walletLedgerService.creditReaderTopUp(anyLong(), anyLong(), any(), any()))
            .thenReturn(WalletPostResult.POSTED);
    }

    @Test
    void createsDistinctVnpayCompatibleOrderNumbers() {
        PayOrderCreation first = service.createPayOrder((byte) 4, 10_000, 1_000, 11L);
        PayOrderCreation second = service.createPayOrder((byte) 4, 10_000, 1_000, 11L);

        assertThat(first.outTradeNo()).isPositive().isNotEqualTo(second.outTradeNo());
        assertThat(first.createTime()).isNotNull();
        ArgumentCaptor<OrderPay> captor = ArgumentCaptor.forClass(OrderPay.class);
        verify(orderPayMapper, org.mockito.Mockito.times(2)).insertSelective(captor.capture());
        assertThat(captor.getAllValues()).allSatisfy(order -> {
            assertThat(order.getPayChannel()).isEqualTo((byte) 4);
            assertThat(order.getTotalAmount()).isEqualTo(10_000);
            assertThat(order.getAccountAmount()).isEqualTo(1_000);
            assertThat(order.getUserId()).isEqualTo(11L);
            assertThat(order.getPayStatus()).isEqualTo((byte) 2);
        });
    }

    @Test
    void retriesWhenDatabaseRejectsADuplicateOrderNumber() {
        when(orderPayMapper.insertSelective(any(OrderPay.class)))
            .thenThrow(new DuplicateKeyException("duplicate order number"))
            .thenReturn(1);

        PayOrderCreation order = service.createPayOrder((byte) 4, 10_000, 1_000, 11L);

        assertThat(order.outTradeNo()).isPositive();
        verify(orderPayMapper, times(2)).insertSelective(any(OrderPay.class));
    }

    @Test
    void creditsExactlyOnceWhenVnpayRetriesIpn() {
        OrderPay pending = pendingOrder();
        when(orderPayMapper.selectOne(any(SelectStatementProvider.class))).thenReturn(Optional.of(pending));
        when(orderPayMapper.update(any(UpdateStatementProvider.class))).thenReturn(1, 0);

        PayOrderUpdateResult first = service.processPayOrder(123L, "456", (byte) 4, 10_000, true);
        PayOrderUpdateResult retry = service.processPayOrder(123L, "456", (byte) 4, 10_000, true);

        assertThat(first).isEqualTo(PayOrderUpdateResult.SUCCESS);
        assertThat(retry).isEqualTo(PayOrderUpdateResult.ALREADY_PROCESSED);
        verify(walletLedgerService).creditReaderTopUp(11L, 1_000L, "123", "VNPAY_TOP_UP:123");
        verify(gamificationEventService).ingest(eq("TOP_UP_SETTLED"), eq("TOPUP:123"), eq(11L),
            isNull(), any(Date.class), isNull());
    }

    @Test
    void rejectsAmountMismatchBeforeUpdatingOrder() {
        when(orderPayMapper.selectOne(any(SelectStatementProvider.class))).thenReturn(Optional.of(pendingOrder()));

        PayOrderUpdateResult result = service.processPayOrder(123L, "456", (byte) 4, 30_000, true);

        assertThat(result).isEqualTo(PayOrderUpdateResult.INVALID_AMOUNT);
        verify(orderPayMapper, never()).update(any(UpdateStatementProvider.class));
        verify(walletLedgerService, never()).creditReaderTopUp(anyLong(), anyLong(), any(), any());
        verifyNoInteractions(gamificationEventService);
    }

    @Test
    void failsTheTransactionWhenTheUserBalanceCannotBeUpdated() {
        when(orderPayMapper.selectOne(any(SelectStatementProvider.class))).thenReturn(Optional.of(pendingOrder()));
        when(orderPayMapper.update(any(UpdateStatementProvider.class))).thenReturn(1);
        when(walletLedgerService.creditReaderTopUp(11L, 1_000L, "123", "VNPAY_TOP_UP:123"))
            .thenThrow(new IllegalStateException("Không thể đồng bộ số dư ví độc giả"));

        assertThatThrownBy(() -> service.processPayOrder(123L, "456", (byte) 4, 10_000, true))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("số dư");
    }

    @Test
    void inspectsReturnDataWithoutChangingTheOrder() {
        when(orderPayMapper.selectOne(any(SelectStatementProvider.class))).thenReturn(Optional.of(pendingOrder()));

        assertThat(service.inspectPayOrder(123L, (byte) 4, 10_000)).isEqualTo(PayOrderState.PENDING);
        assertThat(service.inspectPayOrder(123L, (byte) 4, 30_000)).isEqualTo(PayOrderState.INVALID_AMOUNT);
        verify(orderPayMapper, never()).update(any(UpdateStatementProvider.class));
        verify(walletLedgerService, never()).creditReaderTopUp(anyLong(), anyLong(), any(), any());
    }

    @Test
    void listsAndClaimsEligiblePendingOrders() {
        OrderPay pending = pendingOrder();
        pending.setCreateTime(new Date(1_000));
        pending.setUpdateTime(new Date(2_000));
        when(orderPayMapper.selectMany(any(SelectStatementProvider.class))).thenReturn(List.of(pending));
        when(orderPayMapper.update(any(UpdateStatementProvider.class))).thenReturn(1);

        List<PayOrderSnapshot> orders = service.listPendingPayOrders((byte) 4, new Date(0), new Date(3_000),
            new Date(3_000), 50);

        assertThat(orders).singleElement().satisfies(order -> {
            assertThat(order.outTradeNo()).isEqualTo(123L);
            assertThat(order.accountAmount()).isEqualTo(1_000);
        });
        assertThat(service.claimPendingPayOrder(1L, new Date(2_000), new Date(3_000))).isTrue();
    }

    @Test
    void createsSubscriptionCheckoutFromServerPlanWithoutPromisingXu() {
        ReadingSubscriptionPlanRow plan = subscriptionPlan();
        when(readingSubscriptionMapper.selectActivePlanByCode("BASIC_MONTHLY")).thenReturn(plan);
        when(purchaseMapper.insertPurchase(anyLong(), eq(11L), eq(plan), eq((byte) 4),
            eq("checkout_0001"), any(), eq("v1"), eq("Asia/Ho_Chi_Minh"), any()))
            .thenReturn(1);

        ReadingSubscriptionCheckoutCreation result = service.createSubscriptionCheckout(
            (byte) 4, 11L, "basic_monthly", "checkout_0001");

        assertThat(result.amountVnd()).isEqualTo(49_000);
        ArgumentCaptor<OrderPay> order = ArgumentCaptor.forClass(OrderPay.class);
        verify(orderPayMapper).insertSelective(order.capture());
        assertThat(order.getValue().getTotalAmount()).isEqualTo(49_000);
        assertThat(order.getValue().getAccountAmount()).isZero();
        verify(walletLedgerService, never()).creditReaderTopUp(anyLong(), anyLong(), any(), any());
    }

    @Test
    void successfulSubscriptionSettlementActivatesSnapshotWithoutCreditingXu() {
        OrderPay order = pendingSubscriptionOrder();
        ReadingSubscriptionPurchaseRow purchase = subscriptionPurchase();
        ReadingSubscriptionRow subscription = new ReadingSubscriptionRow();
        subscription.setId(901L);
        when(orderPayMapper.selectOne(any(SelectStatementProvider.class))).thenReturn(Optional.of(order));
        when(purchaseMapper.selectByOutTradeNoForUpdate(123L)).thenReturn(purchase);
        when(orderPayMapper.update(any(UpdateStatementProvider.class))).thenReturn(1);
        when(readingSubscriptionService.activatePurchase(any())).thenReturn(subscription);
        when(purchaseMapper.markActivated(eq(801L), eq(0L), eq(901L), any())).thenReturn(1);

        PayOrderUpdateResult result = service.processPayOrder(
            123L, "BANK-456", (byte) 4, 49_000, true);

        assertThat(result).isEqualTo(PayOrderUpdateResult.SUCCESS);
        ArgumentCaptor<ReadingSubscriptionPurchaseActivationCommand> activation =
            ArgumentCaptor.forClass(ReadingSubscriptionPurchaseActivationCommand.class);
        verify(readingSubscriptionService).activatePurchase(activation.capture());
        assertThat(activation.getValue().planCode()).isEqualTo("BASIC_MONTHLY");
        assertThat(activation.getValue().sourceRef()).isEqualTo("123");
        verify(walletLedgerService, never()).creditReaderTopUp(anyLong(), anyLong(), any(), any());
        verifyNoInteractions(gamificationEventService);
    }

    @Test
    void paidSubscriptionConflictMovesToReviewAndStillAcknowledgesProvider() {
        when(orderPayMapper.selectOne(any(SelectStatementProvider.class)))
            .thenReturn(Optional.of(pendingSubscriptionOrder()));
        when(purchaseMapper.selectByOutTradeNoForUpdate(123L))
            .thenReturn(subscriptionPurchase());
        when(orderPayMapper.update(any(UpdateStatementProvider.class))).thenReturn(1);
        when(readingSubscriptionService.activatePurchase(any())).thenReturn(null);
        when(purchaseMapper.markPaidReview(eq(801L), eq(0L), any())).thenReturn(1);

        assertThat(service.processPayOrder(123L, "BANK-456", (byte) 4, 49_000, true))
            .isEqualTo(PayOrderUpdateResult.SUCCESS);
        verify(purchaseMapper).markPaidReview(eq(801L), eq(0L), any());
        verify(walletLedgerService, never()).creditReaderTopUp(anyLong(), anyLong(), any(), any());
    }

    @Test
    void failedSubscriptionPaymentClosesPurchaseWithoutActivation() {
        when(orderPayMapper.selectOne(any(SelectStatementProvider.class)))
            .thenReturn(Optional.of(pendingSubscriptionOrder()));
        when(purchaseMapper.selectByOutTradeNoForUpdate(123L))
            .thenReturn(subscriptionPurchase());
        when(orderPayMapper.update(any(UpdateStatementProvider.class))).thenReturn(1);
        when(purchaseMapper.markFailed(eq(801L), eq(0L), any())).thenReturn(1);

        assertThat(service.processPayOrder(123L, "BANK-456", (byte) 4, 49_000, false))
            .isEqualTo(PayOrderUpdateResult.SUCCESS);
        verify(purchaseMapper).markFailed(eq(801L), eq(0L), any());
        verify(readingSubscriptionService, never()).activatePurchase(any());
    }

    private OrderPay pendingOrder() {
        OrderPay order = new OrderPay();
        order.setId(1L);
        order.setOutTradeNo(123L);
        order.setPayChannel((byte) 4);
        order.setTotalAmount(10_000);
        order.setAccountAmount(1_000);
        order.setUserId(11L);
        order.setPayStatus((byte) 2);
        return order;
    }

    private OrderPay pendingSubscriptionOrder() {
        OrderPay order = pendingOrder();
        order.setTotalAmount(49_000);
        order.setAccountAmount(0);
        return order;
    }

    private ReadingSubscriptionPlanRow subscriptionPlan() {
        ReadingSubscriptionPlanRow plan = new ReadingSubscriptionPlanRow();
        plan.setId(7L);
        plan.setPlanCode("BASIC_MONTHLY");
        plan.setPlanName("Gói cơ bản");
        plan.setPriceVnd(49_000L);
        plan.setTicketsPerPeriod(10L);
        plan.setPeriodMonths(1);
        plan.setTicketValidityDays(45);
        plan.setStatus("ACTIVE");
        return plan;
    }

    private ReadingSubscriptionPurchaseRow subscriptionPurchase() {
        ReadingSubscriptionPurchaseRow purchase = new ReadingSubscriptionPurchaseRow();
        purchase.setId(801L);
        purchase.setOutTradeNo(123L);
        purchase.setUserId(11L);
        purchase.setPlanId(7L);
        purchase.setPlanCodeSnapshot("BASIC_MONTHLY");
        purchase.setPlanNameSnapshot("Gói cơ bản");
        purchase.setPriceVndSnapshot(49_000L);
        purchase.setTicketsPerPeriodSnapshot(10L);
        purchase.setPeriodMonthsSnapshot(1);
        purchase.setTicketValidityDaysSnapshot(45);
        purchase.setPayChannel((byte) 4);
        purchase.setClientRequestId("checkout_0001");
        purchase.setRequestHash("a".repeat(64));
        purchase.setPolicyVersion("v1");
        purchase.setZoneId("UTC");
        purchase.setStatus("PENDING");
        purchase.setVersion(0L);
        purchase.setCreateTime(new Date());
        return purchase;
    }
}
