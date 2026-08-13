package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.VnpayProperties;
import com.java2nb.novel.core.observability.NovelBusinessMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.java2nb.novel.service.OrderService;
import com.java2nb.novel.service.PayOrderSnapshot;
import com.java2nb.novel.service.PayOrderUpdateResult;
import com.java2nb.novel.service.VnpayQueryResult;
import com.java2nb.novel.service.VnpayQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VnpayReconciliationScheduleTest {

    private OrderService orderService;
    private VnpayQueryService queryService;
    private VnpayReconciliationSchedule schedule;
    private PayOrderSnapshot order;

    @BeforeEach
    void setUp() {
        VnpayProperties properties = new VnpayProperties();
        properties.setEnabled(true);
        properties.setTmnCode("DEMOV210");
        properties.setHashSecret("query-test-secret");
        properties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("https://merchant.example/pay/vnpay/return");
        orderService = mock(OrderService.class);
        queryService = mock(VnpayQueryService.class);
        schedule = new VnpayReconciliationSchedule(properties, orderService, queryService,
            new NovelBusinessMetrics(new SimpleMeterRegistry()));
        order = new PayOrderSnapshot(1L, 123L, 10_000, 1_000,
            Date.from(Instant.now().minusSeconds(1_800)), Date.from(Instant.now().minusSeconds(600)));
    }

    @Test
    void claimsAndCreditsAConfirmedPendingOrder() {
        when(orderService.listPendingPayOrders(anyByte(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(order));
        when(orderService.claimPendingPayOrder(eq(1L), any(), any())).thenReturn(true);
        when(queryService.query(order)).thenReturn(new VnpayQueryResult(VnpayQueryResult.Status.SUCCESS, "456"));
        when(orderService.processPayOrder(123L, "456", (byte) 4, 10_000, true))
            .thenReturn(PayOrderUpdateResult.SUCCESS);

        schedule.reconcilePendingOrders();

        verify(orderService).processPayOrder(123L, "456", (byte) 4, 10_000, true);
    }

    @Test
    void doesNotQueryAnOrderClaimedByAnotherReplica() {
        when(orderService.listPendingPayOrders(anyByte(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(order));
        when(orderService.claimPendingPayOrder(eq(1L), any(), any())).thenReturn(false);

        schedule.reconcilePendingOrders();

        verify(queryService, never()).query(any());
        verify(orderService, never()).processPayOrder(any(), any(), anyByte(), anyInt(), eq(true));
    }

    @Test
    void recordsAFinalFailureWithoutCreditingTheUser() {
        when(orderService.listPendingPayOrders(anyByte(), any(), any(), any(), anyInt()))
            .thenReturn(List.of(order));
        when(orderService.claimPendingPayOrder(eq(1L), any(), any())).thenReturn(true);
        when(queryService.query(order)).thenReturn(new VnpayQueryResult(VnpayQueryResult.Status.FAILED, "456"));
        when(orderService.processPayOrder(123L, "456", (byte) 4, 10_000, false))
            .thenReturn(PayOrderUpdateResult.SUCCESS);

        schedule.reconcilePendingOrders();

        verify(orderService).processPayOrder(123L, "456", (byte) 4, 10_000, false);
        verify(orderService, never()).processPayOrder(123L, "456", (byte) 4, 10_000, true);
    }
}
