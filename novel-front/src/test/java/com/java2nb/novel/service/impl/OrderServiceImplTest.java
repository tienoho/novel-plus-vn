package com.java2nb.novel.service.impl;

import com.java2nb.novel.entity.OrderPay;
import com.java2nb.novel.mapper.OrderPayMapper;
import com.java2nb.novel.service.PayOrderCreation;
import com.java2nb.novel.service.PayOrderSnapshot;
import com.java2nb.novel.service.PayOrderState;
import com.java2nb.novel.service.PayOrderUpdateResult;
import com.java2nb.novel.service.UserService;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceImplTest {

    private OrderPayMapper orderPayMapper;
    private UserService userService;
    private OrderServiceImpl service;

    @BeforeEach
    void setUp() {
        orderPayMapper = mock(OrderPayMapper.class);
        userService = mock(UserService.class);
        service = new OrderServiceImpl(orderPayMapper, userService);
        when(orderPayMapper.insertSelective(any(OrderPay.class))).thenReturn(1);
        when(userService.addAmount(anyLong(), anyInt())).thenReturn(true);
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
        verify(userService).addAmount(11L, 1_000);
    }

    @Test
    void rejectsAmountMismatchBeforeUpdatingOrder() {
        when(orderPayMapper.selectOne(any(SelectStatementProvider.class))).thenReturn(Optional.of(pendingOrder()));

        PayOrderUpdateResult result = service.processPayOrder(123L, "456", (byte) 4, 30_000, true);

        assertThat(result).isEqualTo(PayOrderUpdateResult.INVALID_AMOUNT);
        verify(orderPayMapper, never()).update(any(UpdateStatementProvider.class));
        verify(userService, never()).addAmount(any(Long.class), anyInt());
    }

    @Test
    void failsTheTransactionWhenTheUserBalanceCannotBeUpdated() {
        when(orderPayMapper.selectOne(any(SelectStatementProvider.class))).thenReturn(Optional.of(pendingOrder()));
        when(orderPayMapper.update(any(UpdateStatementProvider.class))).thenReturn(1);
        when(userService.addAmount(11L, 1_000)).thenReturn(false);

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
        verify(userService, never()).addAmount(any(Long.class), anyInt());
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
}
