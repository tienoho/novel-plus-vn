package com.java2nb.novel.service.impl;

import com.java2nb.novel.entity.OrderPay;
import com.java2nb.novel.entity.OrderRefund;
import com.java2nb.novel.mapper.OrderPayMapper;
import com.java2nb.novel.mapper.OrderRefundMapper;
import com.java2nb.novel.mapper.WalletLedgerMapper;
import com.java2nb.novel.service.wallet.LedgerTransactionRow;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import com.java2nb.novel.service.wallet.WalletPostResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RefundServiceImplTest {

    private OrderRefundMapper orderRefundMapper;
    private OrderPayMapper orderPayMapper;
    private WalletLedgerMapper walletLedgerMapper;
    private WalletLedgerService walletLedgerService;
    private RefundServiceImpl refundService;

    @BeforeEach
    void setUp() {
        orderRefundMapper = mock(OrderRefundMapper.class);
        orderPayMapper = mock(OrderPayMapper.class);
        walletLedgerMapper = mock(WalletLedgerMapper.class);
        walletLedgerService = mock(WalletLedgerService.class);
        refundService = new RefundServiceImpl(orderRefundMapper, orderPayMapper, walletLedgerMapper,
            walletLedgerService);
        when(orderRefundMapper.insert(any())).thenAnswer(invocation -> {
            OrderRefund refund = invocation.getArgument(0);
            if (refund.getId() == null) {
                refund.setId(1L);
            }
            return 1;
        });
        when(orderRefundMapper.insertAudit(anyLong(), anyString(), anyString(), nullable(String.class),
            anyString(), anyString(), nullable(Long.class), nullable(String.class))).thenReturn(1);
        when(orderRefundMapper.transitionStatus(anyLong(), anyString(), anyString(), nullable(Long.class),
            nullable(Long.class), nullable(String.class), anyLong(), nullable(String.class))).thenReturn(1);
    }

    @Test
    void requestRefundIsDeterministicAndAudited() {
        long userId = 100L;
        long outTradeNo = 123456L;
        OrderPay orderPay = paidOrder(userId, outTradeNo, 50_000, 500);
        LedgerTransactionRow original = transaction(10L, "VNPAY_TOP_UP:" + outTradeNo);

        when(orderPayMapper.selectOne(any(SelectStatementProvider.class))).thenReturn(Optional.of(orderPay));
        when(walletLedgerMapper.selectTransactionByIdempotencyKey("VNPAY_TOP_UP:" + outTradeNo))
            .thenReturn(original);

        OrderRefund refund = refundService.requestRefund(userId, outTradeNo, "Muốn hoàn tiền");

        assertThat(refund.getRefundNo()).isEqualTo("RF-" + outTradeNo);
        assertThat(refund.getStatus()).isEqualTo("REQUESTED");
        assertThat(refund.getOriginalLedgerTransactionId()).isEqualTo(10L);
        verify(orderRefundMapper).insert(refund);
        verify(orderRefundMapper).insertAudit(refund.getId(), refund.getRefundNo(), "REQUEST_CREATED",
            null, "REQUESTED", "USER", userId, "Muốn hoàn tiền");
    }

    @Test
    void approveRefundHoldsXuAndUsesOptimisticTransition() {
        OrderRefund requested = refund(1L, "RF-123456", "REQUESTED");
        OrderRefund approved = refund(1L, "RF-123456", "APPROVED");
        LedgerTransactionRow hold = transaction(20L, "REFUND_HOLD:RF-123456");
        when(orderRefundMapper.selectById(1L)).thenReturn(requested, approved);
        when(walletLedgerService.holdReaderRefund(100L, 500L, "RF-123456", "REFUND_HOLD:RF-123456"))
            .thenReturn(WalletPostResult.POSTED);
        when(walletLedgerMapper.selectTransactionByIdempotencyKey("REFUND_HOLD:RF-123456")).thenReturn(hold);

        OrderRefund result = refundService.approveRefund(1L, 99L);

        assertThat(result.getStatus()).isEqualTo("APPROVED");
        verify(orderRefundMapper).transitionStatus(1L, "REQUESTED", "APPROVED", 20L, null,
            null, 99L, null);
    }

    @Test
    void confirmRefundSettlesHoldAndStoresProviderReference() {
        OrderRefund approved = refund(1L, "RF-123456", "APPROVED");
        OrderRefund reversed = refund(1L, "RF-123456", "REVERSED");
        reversed.setProviderReference("VNPAY-REF-01");
        LedgerTransactionRow settlement = transaction(30L, "REFUND_SETTLED:RF-123456");
        when(orderRefundMapper.selectById(1L)).thenReturn(approved, reversed);
        when(walletLedgerService.settleReaderRefund(500L, "RF-123456", "REFUND_SETTLED:RF-123456"))
            .thenReturn(WalletPostResult.POSTED);
        when(walletLedgerMapper.selectTransactionByIdempotencyKey("REFUND_SETTLED:RF-123456"))
            .thenReturn(settlement);

        OrderRefund result = refundService.confirmRefund(1L, "VNPAY-REF-01", 99L);

        assertThat(result.getStatus()).isEqualTo("REVERSED");
        verify(orderRefundMapper).transitionStatus(1L, "APPROVED", "REVERSED", null, 30L,
            "VNPAY-REF-01", 99L, null);
    }

    @Test
    void chargebackMustBeFullAmount() {
        when(orderPayMapper.selectOne(any(SelectStatementProvider.class)))
            .thenReturn(Optional.of(paidOrder(100L, 123456L, 50_000, 500)));

        assertThatThrownBy(() -> refundService.recordChargeback(123456L, 10_000,
            "Ngân hàng báo chargeback", 99L))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("toàn phần");
        verifyNoInteractions(walletLedgerService);
    }

    private OrderPay paidOrder(long userId, long outTradeNo, int amountVnd, int xu) {
        OrderPay orderPay = new OrderPay();
        orderPay.setOutTradeNo(outTradeNo);
        orderPay.setUserId(userId);
        orderPay.setPayStatus((byte) 1);
        orderPay.setTotalAmount(amountVnd);
        orderPay.setAccountAmount(xu);
        return orderPay;
    }

    private OrderRefund refund(long id, String refundNo, String status) {
        return OrderRefund.builder()
            .id(id)
            .refundNo(refundNo)
            .outTradeNo(123456L)
            .userId(100L)
            .refundAmountVnd(50_000)
            .refundXu(500L)
            .type("REFUND")
            .status(status)
            .reason("Muốn hoàn tiền")
            .build();
    }

    private LedgerTransactionRow transaction(long id, String idempotencyKey) {
        LedgerTransactionRow transaction = new LedgerTransactionRow();
        transaction.setId(id);
        transaction.setIdempotencyKey(idempotencyKey);
        transaction.setBusinessType("TOP_UP");
        transaction.setBusinessId("123456");
        transaction.setTotalAmount(500L);
        return transaction;
    }
}
