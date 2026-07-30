package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.GamificationProperties;
import com.java2nb.novel.mapper.MonthlyTicketMapper;
import com.java2nb.novel.mapper.MonthlyRankingMapper;
import com.java2nb.novel.mapper.WalletLedgerMapper;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LedgerIntegrityCheckScheduleTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2027-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void testAuditCheckPassesWhenNoDiscrepancies() {
        WalletLedgerMapper mapper = mock(WalletLedgerMapper.class);
        MonthlyTicketMapper ticketMapper = emptyTicketAuditMapper();
        MonthlyRankingMapper rankingMapper = emptyRankingAuditMapper();
        when(mapper.checkZeroSumLedger()).thenReturn(List.of());
        when(mapper.checkProjectionMismatch()).thenReturn(List.of());
        GamificationProperties properties = new GamificationProperties();

        LedgerIntegrityCheckSchedule schedule = new LedgerIntegrityCheckSchedule(
            mapper, ticketMapper, rankingMapper, properties, CLOCK);

        schedule.runLedgerIntegrityAudit();

        assertThat(schedule.performAuditCheck()).isTrue();

        // Cutoff phải được tính ở Java (Instant.now(clock) trừ đúng ngưỡng cấu hình) và truyền
        // thẳng xuống làm tham số, không để SQL tự cộng trừ bằng TIMESTAMPADD với tham số động —
        // ShardingSphere hiểu nhầm tên đơn vị thời gian thành tên cột trong trường hợp đó.
        Date expectedJobCutoff = Date.from(Instant.parse("2027-01-01T00:00:00Z")
            .minusSeconds(properties.getJob().getLeaseSeconds()));
        Date expectedReviewCutoff = Date.from(Instant.parse("2027-01-01T00:00:00Z")
            .minusSeconds(properties.getSeason().getReviewWindowHours() * 3600L));
        Date expectedClaimCutoff = Date.from(Instant.parse("2027-01-01T00:00:00Z")
            .minusSeconds(properties.getReward().getClaimWindowDays() * 86400L));

        verify(ticketMapper, times(2)).checkStuckJobs(expectedJobCutoff, expectedReviewCutoff);
        verify(ticketMapper, times(2)).checkPendingRewards(expectedClaimCutoff);
    }

    @Test
    void testAuditCheckFailsWhenDiscrepancyFound() {
        WalletLedgerMapper mapper = mock(WalletLedgerMapper.class);
        MonthlyTicketMapper ticketMapper = emptyTicketAuditMapper();
        MonthlyRankingMapper rankingMapper = emptyRankingAuditMapper();
        when(mapper.checkZeroSumLedger()).thenReturn(List.of(Map.of("ledger_transaction_id", 123L, "sum_amount", 50L)));
        when(mapper.checkProjectionMismatch()).thenReturn(List.of());

        LedgerIntegrityCheckSchedule schedule = new LedgerIntegrityCheckSchedule(
            mapper, ticketMapper, rankingMapper, new GamificationProperties(), CLOCK);

        assertThat(schedule.performAuditCheck()).isFalse();
    }

    @Test
    void testAuditCheckFailsWhenTicketProjectionDrifts() {
        WalletLedgerMapper mapper = mock(WalletLedgerMapper.class);
        MonthlyTicketMapper ticketMapper = emptyTicketAuditMapper();
        MonthlyRankingMapper rankingMapper = emptyRankingAuditMapper();
        when(mapper.checkZeroSumLedger()).thenReturn(List.of());
        when(mapper.checkProjectionMismatch()).thenReturn(List.of());
        when(ticketMapper.checkAccountLotDrift())
            .thenReturn(List.of(Map.of("user_id", 99L, "available_balance", 2L, "lot_remaining", 1L)));

        LedgerIntegrityCheckSchedule schedule = new LedgerIntegrityCheckSchedule(
            mapper, ticketMapper, rankingMapper, new GamificationProperties(), CLOCK);

        assertThat(schedule.performAuditCheck()).isFalse();
    }

    private MonthlyTicketMapper emptyTicketAuditMapper() {
        MonthlyTicketMapper mapper = mock(MonthlyTicketMapper.class);
        when(mapper.checkAccountLotDrift()).thenReturn(List.of());
        when(mapper.checkAllocationImbalance()).thenReturn(List.of());
        when(mapper.checkOrphanLots()).thenReturn(List.of());
        when(mapper.checkStuckJobs(any(Date.class), any(Date.class))).thenReturn(List.of());
        when(mapper.checkPendingRewards(any(Date.class))).thenReturn(List.of());
        return mapper;
    }

    private MonthlyRankingMapper emptyRankingAuditMapper() {
        MonthlyRankingMapper mapper = mock(MonthlyRankingMapper.class);
        when(mapper.checkRankCounterDrift(null)).thenReturn(List.of());
        return mapper;
    }
}
