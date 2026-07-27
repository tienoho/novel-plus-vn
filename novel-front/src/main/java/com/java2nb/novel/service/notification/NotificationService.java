package com.java2nb.novel.service.notification;

import io.github.xxyopen.model.page.PageBean;

import java.util.Date;
import java.util.List;

public interface NotificationService {
    List<Long> listPendingEventIds(Date now, int limit);
    boolean processEvent(long eventId);
    void recordFailure(long eventId, String error);

    void followAuthor(long userId, long authorId);
    void unfollowAuthor(long userId, long authorId);
    boolean isFollowingAuthor(long userId, long authorId);

    PageBean<UserNotificationRow> listNotifications(long userId, int page, int pageSize);
    long countUnread(long userId);
    void markRead(long userId, long notificationId);
    void markAllRead(long userId);
}

