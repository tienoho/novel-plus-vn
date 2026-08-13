package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.ReaderEntitlementProperties;
import com.java2nb.novel.mapper.ReadingTicketMapper;
import com.java2nb.novel.service.entitlement.ReadingTicketExpiryResult;
import com.java2nb.novel.service.entitlement.ReadingTicketService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingTicketExpiryScheduleTest {
    private static final Instant NOW = Instant.parse("2027-01-01T03:00:00Z");
    private static final Date CUTOFF = Date.from(NOW);
    private ReaderEntitlementProperties properties;
    private ReadingTicketMapper mapper;
    private ReadingTicketService service;
    private ReadingTicketExpirySchedule schedule;

    @BeforeEach
    void setUp() {
        properties = new ReaderEntitlementProperties();
        properties.setEnabled(true);
        properties.setExpiryBatchSize(2);
        properties.setExpiryMaxLotsPerUser(100);
        mapper = mock(ReadingTicketMapper.class);
        service = mock(ReadingTicketService.class);
        schedule = new ReadingTicketExpirySchedule(properties, mapper, service,
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void processesDueUsersUntilBatchIsEmpty() {
        when(mapper.selectExpiredLotUserIds(CUTOFF, 2))
            .thenReturn(List.of(101L, 202L), List.of());
        when(service.expireDueLots(101L, CUTOFF, "v1", 100))
            .thenReturn(new ReadingTicketExpiryResult(2, 5));
        when(service.expireDueLots(202L, CUTOFF, "v1", 100))
            .thenReturn(new ReadingTicketExpiryResult(1, 3));

        schedule.expireDueLots();

        verify(service).expireDueLots(101L, CUTOFF, "v1", 100);
        verify(service).expireDueLots(202L, CUTOFF, "v1", 100);
    }

    @Test
    void disabledFeatureDoesNotScanDatabase() {
        properties.setEnabled(false);

        schedule.expireDueLots();

        verify(mapper, never()).selectExpiredLotUserIds(any(), anyInt());
    }
}
