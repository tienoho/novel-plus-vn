package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.config.ReaderEntitlementProperties;
import com.java2nb.novel.core.observability.NovelBusinessMetrics;
import com.java2nb.novel.service.VnpayRecurringRenewalProcessor;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalResult;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.Date;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReadingSubscriptionRenewalScheduleTest {

    @Test
    void reconcilesProviderPendingCyclesBeforeProcessingNewAttempts() {
        ReaderEntitlementProperties properties = new ReaderEntitlementProperties();
        properties.setEnabled(true);
        properties.setSubscriptionRenewalEnabled(true);
        ReadingSubscriptionRenewalService service = mock(ReadingSubscriptionRenewalService.class);
        VnpayRecurringRenewalProcessor processor = mock(VnpayRecurringRenewalProcessor.class);
        when(service.listDueSubscriptionIds(any(Date.class), eq(100))).thenReturn(List.of());
        when(service.listProviderPendingCycleIds(any(Date.class), eq(100))).thenReturn(List.of(81L));
        when(service.listDueCycleIds(any(Date.class), eq(100))).thenReturn(List.of());
        when(processor.reconcile(eq(81L), any(Date.class)))
            .thenReturn(ReadingSubscriptionRenewalResult.PROVIDER_PENDING);
        ReadingSubscriptionRenewalSchedule schedule = new ReadingSubscriptionRenewalSchedule(
            properties, service, processor,
            new NovelBusinessMetrics(new SimpleMeterRegistry()));

        schedule.renewDueSubscriptions();

        verify(processor).reconcile(eq(81L), any(Date.class));
    }
}
