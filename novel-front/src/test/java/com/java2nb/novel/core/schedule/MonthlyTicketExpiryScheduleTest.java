package com.java2nb.novel.core.schedule;

import com.java2nb.novel.mapper.MonthlyTicketMapper;
import com.java2nb.novel.service.gamification.MonthlyTicketService;
import com.java2nb.novel.service.gamification.TicketExpiryResult;
import com.java2nb.novel.service.gamification.config.GamificationConfigProvider;
import com.java2nb.novel.service.gamification.config.GamificationConfigSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MonthlyTicketExpiryScheduleTest {

    private static final Instant NOW = Instant.parse("2026-08-15T03:00:00Z");
    private static final Date CUTOFF = Date.from(NOW);

    private GamificationConfigProvider configProvider;
    private MonthlyTicketMapper mapper;
    private MonthlyTicketService service;
    private MonthlyTicketExpirySchedule schedule;

    @BeforeEach
    void setUp() {
        configProvider = mock(GamificationConfigProvider.class);
        when(configProvider.currentForWrite()).thenReturn(
            GamificationConfigSnapshot.bootstrapDisabled().toBuilder().ticketEnabled(true).build());
        mapper = mock(MonthlyTicketMapper.class);
        service = mock(MonthlyTicketService.class);
        schedule = new MonthlyTicketExpirySchedule(configProvider, mapper, service,
            Clock.fixed(NOW, ZoneOffset.UTC), "expiry-test-instance");
    }

    @Test
    void claimsDateAndProcessesEachUserThroughTheTransactionalService() {
        when(mapper.insertJobRunIgnore("LOT_EXPIRY", "DATE", "2026-08-15",
            "expiry-test-instance", CUTOFF)).thenReturn(1);
        when(mapper.selectExpiredLotUserIds(CUTOFF, 0L, 500)).thenReturn(List.of(101L, 202L));
        when(service.expireDueLots(101L, CUTOFF, "2026-08-15", "v1"))
            .thenReturn(new TicketExpiryResult(2, 5));
        when(service.expireDueLots(202L, CUTOFF, "2026-08-15", "v1"))
            .thenReturn(new TicketExpiryResult(1, 3));
        when(mapper.advanceJobCheckpoint(anyString(), anyString(), anyString(), anyString(),
            anyString(), anyLong(), any(Date.class))).thenReturn(1);
        when(mapper.completeJobRun("LOT_EXPIRY", "DATE", "2026-08-15",
            "expiry-test-instance", CUTOFF)).thenReturn(1);

        schedule.expireDueLots();

        verify(service).expireDueLots(101L, CUTOFF, "2026-08-15", "v1");
        verify(service).expireDueLots(202L, CUTOFF, "2026-08-15", "v1");
        verify(mapper).advanceJobCheckpoint("LOT_EXPIRY", "DATE", "2026-08-15",
            "expiry-test-instance", "101", 2, CUTOFF);
        verify(mapper).advanceJobCheckpoint("LOT_EXPIRY", "DATE", "2026-08-15",
            "expiry-test-instance", "202", 1, CUTOFF);
        verify(mapper).completeJobRun("LOT_EXPIRY", "DATE", "2026-08-15",
            "expiry-test-instance", CUTOFF);
    }

    @Test
    void doesNothingWhenAnotherReplicaOwnsTheDate() {
        when(mapper.insertJobRunIgnore(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(0);
        when(mapper.claimStaleJobRun(anyString(), anyString(), anyString(), anyString(), any(), any()))
            .thenReturn(0);

        schedule.expireDueLots();

        verify(service, never()).expireDueLots(anyLong(), any(), anyString(), anyString());
    }

    @Test
    void marksOwnedJobFailedWhenAUserTransactionFails() {
        when(mapper.insertJobRunIgnore(anyString(), anyString(), anyString(), anyString(), any()))
            .thenReturn(1);
        when(mapper.selectExpiredLotUserIds(CUTOFF, 0L, 500)).thenReturn(List.of(101L));
        when(service.expireDueLots(101L, CUTOFF, "2026-08-15", "v1"))
            .thenThrow(new IllegalStateException("projection drift"));
        when(mapper.failJobRun(anyString(), anyString(), anyString(), anyString(), any(), anyString()))
            .thenReturn(1);

        schedule.expireDueLots();

        verify(mapper).failJobRun("LOT_EXPIRY", "DATE", "2026-08-15",
            "expiry-test-instance", CUTOFF, "IllegalStateException: projection drift");
        verify(mapper, never()).completeJobRun(anyString(), anyString(), anyString(), anyString(), any());
    }
}
