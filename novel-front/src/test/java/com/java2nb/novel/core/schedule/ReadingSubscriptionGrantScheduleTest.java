package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.ReaderEntitlementProperties;
import com.java2nb.novel.mapper.ReadingSubscriptionMapper;
import com.java2nb.novel.service.subscription.ReadingSubscriptionGrantResult;
import com.java2nb.novel.service.subscription.ReadingSubscriptionGrantStatus;
import com.java2nb.novel.service.subscription.ReadingSubscriptionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingSubscriptionGrantScheduleTest {
    private static final Instant NOW = Instant.parse("2027-01-01T00:00:00Z");
    private static final Date NOW_DATE = Date.from(NOW);
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

    private ReaderEntitlementProperties properties;
    private ReadingSubscriptionMapper mapper;
    private ReadingSubscriptionService service;
    private ReadingSubscriptionGrantSchedule schedule;

    @BeforeEach
    void setUp() {
        properties = new ReaderEntitlementProperties();
        properties.setEnabled(true);
        properties.setSubscriptionGrantBatchSize(2);
        properties.setSubscriptionZoneId(BUSINESS_ZONE.getId());
        mapper = mock(ReadingSubscriptionMapper.class);
        service = mock(ReadingSubscriptionService.class);
        schedule = new ReadingSubscriptionGrantSchedule(properties, mapper, service,
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void grantsOneConfiguredBatchOfDueSubscriptions() {
        when(mapper.selectDueSubscriptionIds(NOW_DATE, 2)).thenReturn(List.of(51L, 52L));
        when(service.grantDuePeriod(51L, NOW_DATE, BUSINESS_ZONE, "v1"))
            .thenReturn(posted(51L));
        when(service.grantDuePeriod(52L, NOW_DATE, BUSINESS_ZONE, "v1"))
            .thenReturn(posted(52L));

        schedule.grantDuePeriods();

        verify(mapper).selectDueSubscriptionIds(NOW_DATE, 2);
        verify(service).grantDuePeriod(51L, NOW_DATE, BUSINESS_ZONE, "v1");
        verify(service).grantDuePeriod(52L, NOW_DATE, BUSINESS_ZONE, "v1");
    }

    @Test
    void failureOnOneSubscriptionDoesNotStopTheRemainingBatch() {
        IllegalStateException failure = new IllegalStateException("grant failed");
        when(mapper.selectDueSubscriptionIds(NOW_DATE, 2)).thenReturn(List.of(51L, 52L));
        when(service.grantDuePeriod(51L, NOW_DATE, BUSINESS_ZONE, "v1"))
            .thenThrow(failure);
        when(service.grantDuePeriod(52L, NOW_DATE, BUSINESS_ZONE, "v1"))
            .thenReturn(posted(52L));

        schedule.grantDuePeriods();

        verify(service).grantDuePeriod(51L, NOW_DATE, BUSINESS_ZONE, "v1");
        verify(service).grantDuePeriod(52L, NOW_DATE, BUSINESS_ZONE, "v1");
    }

    @Test
    void disabledFeatureDoesNotScanDatabase() {
        properties.setEnabled(false);

        schedule.grantDuePeriods();

        verify(mapper, never()).selectDueSubscriptionIds(any(), anyInt());
    }

    private ReadingSubscriptionGrantResult posted(long subscriptionId) {
        return new ReadingSubscriptionGrantResult(ReadingSubscriptionGrantStatus.POSTED,
            subscriptionId, NOW_DATE, Date.from(NOW.plusSeconds(31L * 24 * 60 * 60)), 10);
    }
}
