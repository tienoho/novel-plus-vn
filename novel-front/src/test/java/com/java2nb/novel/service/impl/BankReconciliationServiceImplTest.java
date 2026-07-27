package com.java2nb.novel.service.impl;

import com.java2nb.novel.entity.BankReconciliationBatch;
import com.java2nb.novel.entity.OrderPay;
import com.java2nb.novel.mapper.BankReconciliationMapper;
import com.java2nb.novel.mapper.OrderPayMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.dynamic.sql.select.render.SelectStatementProvider;

import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class BankReconciliationServiceImplTest {

    private BankReconciliationMapper bankReconciliationMapper;
    private OrderPayMapper orderPayMapper;
    private BankReconciliationServiceImpl service;

    @BeforeEach
    void setUp() {
        bankReconciliationMapper = mock(BankReconciliationMapper.class);
        orderPayMapper = mock(OrderPayMapper.class);
        service = new BankReconciliationServiceImpl(bankReconciliationMapper, orderPayMapper);
    }

    @Test
    void testProcessStatementFileMatchingEngine() {
        String csvContent = "outTradeNo,bankTradeNo,amountVnd\n" +
            "10001,BANK_TX_1,50000\n" +
            "10002,BANK_TX_2,100000\n" +
            "99999,BANK_TX_3,20000";

        OrderPay pay1 = new OrderPay();
        pay1.setOutTradeNo(10001L);
        pay1.setTotalAmount(50000);

        OrderPay pay2 = new OrderPay();
        pay2.setOutTradeNo(10002L);
        pay2.setTotalAmount(80000); // Mismatch amount

        when(orderPayMapper.selectOne(any(SelectStatementProvider.class)))
            .thenAnswer(invocation -> {
                SelectStatementProvider provider = invocation.getArgument(0);
                Map<String, Object> params = provider.getParameters();
                if (params != null && (params.containsValue(10001L) || params.containsValue("10001") || params.containsValue(10001))) return Optional.of(pay1);
                if (params != null && (params.containsValue(10002L) || params.containsValue("10002") || params.containsValue(10002))) return Optional.of(pay2);
                return Optional.empty();
            });

        BankReconciliationBatch batch = service.processStatementFile((byte) 4, new Date(), "test.csv", csvContent.getBytes(StandardCharsets.UTF_8));

        assertThat(batch).isNotNull();
        assertThat(batch.getTotalTransactions()).isEqualTo(3);
        assertThat(batch.getMatchedTransactions()).isEqualTo(1);
        assertThat(batch.getMismatchedTransactions()).isEqualTo(2);
        assertThat(batch.getStatus()).isEqualTo("COMPLETED");

        verify(bankReconciliationMapper, times(3)).insertItem(any());
        verify(bankReconciliationMapper).updateBatchSummary(any());
    }
}
