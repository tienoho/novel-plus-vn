package com.java2nb.novel.core.schedule;

import com.java2nb.novel.mapper.WalletLedgerMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LedgerIntegrityCheckScheduleTest {

    @Test
    void testAuditCheckPassesWhenNoDiscrepancies() {
        WalletLedgerMapper mapper = mock(WalletLedgerMapper.class);
        when(mapper.checkZeroSumLedger()).thenReturn(List.of());
        when(mapper.checkProjectionMismatch()).thenReturn(List.of());

        LedgerIntegrityCheckSchedule schedule = new LedgerIntegrityCheckSchedule(mapper);

        schedule.runLedgerIntegrityAudit();

        assertThat(schedule.performAuditCheck()).isTrue();
    }

    @Test
    void testAuditCheckFailsWhenDiscrepancyFound() {
        WalletLedgerMapper mapper = mock(WalletLedgerMapper.class);
        when(mapper.checkZeroSumLedger()).thenReturn(List.of(Map.of("ledger_transaction_id", 123L, "sum_amount", 50L)));
        when(mapper.checkProjectionMismatch()).thenReturn(List.of());

        LedgerIntegrityCheckSchedule schedule = new LedgerIntegrityCheckSchedule(mapper);

        assertThat(schedule.performAuditCheck()).isFalse();
    }
}
