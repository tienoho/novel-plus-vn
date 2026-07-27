package com.java2nb.novel.core.schedule;

import com.java2nb.novel.service.notification.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Date;

/** Fan-out outbox chương mới sang hộp thông báo độc giả. */
@Component
@RequiredArgsConstructor
@Slf4j
public class ChapterNotificationSchedule {
    private final NotificationService notificationService;

    @Scheduled(fixedDelayString = "${notification.chapter.schedule-delay-ms:15000}",
        initialDelayString = "${notification.chapter.schedule-initial-delay-ms:15000}")
    public void deliverPublishedChapters() {
        for (Long eventId : notificationService.listPendingEventIds(new Date(), 20)) {
            try {
                notificationService.processEvent(eventId);
            } catch (Exception exception) {
                log.error("Không thể phát thông báo cho sự kiện chương {}", eventId, exception);
                notificationService.recordFailure(eventId, exception.getMessage());
            }
        }
    }
}

