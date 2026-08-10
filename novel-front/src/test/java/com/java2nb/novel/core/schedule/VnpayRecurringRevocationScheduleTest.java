package com.java2nb.novel.core.schedule;

import com.java2nb.novel.core.observability.NovelBusinessMetrics;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.RenewalQueue;
import com.java2nb.novel.service.VnpayRecurringRevocationProcessor;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VnpayRecurringRevocationScheduleTest {

    @Test
    void exportsPendingQueueEvenWhenNoMandateIsDue() {
        VnpayRecurringRevocationProcessor processor =
            mock(VnpayRecurringRevocationProcessor.class);
        NovelBusinessMetrics metrics = mock(NovelBusinessMetrics.class);
        when(processor.listDueIds(any())).thenReturn(List.of());
        when(processor.countPending()).thenReturn(3L);

        new VnpayRecurringRevocationSchedule(processor, metrics).revokeDueMandates();

        verify(metrics).setRenewalQueue(RenewalQueue.MANDATE_REVOKE_PENDING, 3L);
    }
}
