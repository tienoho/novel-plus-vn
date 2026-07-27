package com.java2nb.novel.core.schedule;

import com.java2nb.novel.service.notification.NotificationService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChapterNotificationScheduleTest {

    @Test
    void failedEventIsRecordedWithoutBlockingFollowingEvents() {
        NotificationService service = mock(NotificationService.class);
        when(service.listPendingEventIds(any(), eq(20))).thenReturn(List.of(11L, 12L));
        doThrow(new IllegalStateException("fan-out failed")).when(service).processEvent(11L);

        new ChapterNotificationSchedule(service).deliverPublishedChapters();

        verify(service).recordFailure(11L, "fan-out failed");
        verify(service).processEvent(12L);
    }
}

