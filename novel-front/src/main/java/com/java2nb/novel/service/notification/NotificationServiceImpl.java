package com.java2nb.novel.service.notification;

import com.github.pagehelper.PageHelper;
import com.java2nb.novel.core.i18n.Messages;
import com.java2nb.novel.mapper.NotificationMapper;
import io.github.xxyopen.model.page.PageBean;
import io.github.xxyopen.model.page.builder.pagehelper.PageBuilder;
import lombok.RequiredArgsConstructor;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {
    private final NotificationMapper notificationMapper;
    private final Messages messages;

    @Override
    public List<Long> listPendingEventIds(Date now, int limit) {
        return notificationMapper.listPendingEventIds(now, Math.max(1, Math.min(limit, 100)));
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean processEvent(long eventId) {
        if (notificationMapper.claimEvent(eventId) != 1) {
            return false;
        }
        ChapterPublishEventRow event = notificationMapper.selectClaimedEvent(eventId);
        if (event == null) {
            throw new IllegalStateException(messages.get("notification.event.missing"));
        }
        notificationMapper.fanOut(event);
        if (notificationMapper.markProcessed(eventId, new Date()) != 1) {
            throw new IllegalStateException(messages.get("notification.event.concurrent"));
        }
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void recordFailure(long eventId, String error) {
        notificationMapper.recordFailure(eventId,
            StringUtils.abbreviate(StringUtils.defaultString(error, messages.get("error.internal")), 500),
            new Date());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void followAuthor(long userId, long authorId) {
        if (userId <= 0 || authorId <= 0 || notificationMapper.authorExists(authorId) == 0) {
            throw new IllegalArgumentException(messages.get("notification.author.invalid"));
        }
        notificationMapper.followAuthor(userId, authorId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void unfollowAuthor(long userId, long authorId) {
        notificationMapper.unfollowAuthor(userId, authorId);
    }

    @Override
    public boolean isFollowingAuthor(long userId, long authorId) {
        return userId > 0 && authorId > 0 && notificationMapper.isFollowingAuthor(userId, authorId) > 0;
    }

    @Override
    public PageBean<UserNotificationRow> listNotifications(long userId, int page, int pageSize) {
        PageHelper.startPage(Math.max(1, page), Math.max(1, Math.min(pageSize, 100)));
        return PageBuilder.build(notificationMapper.listNotifications(userId));
    }

    @Override
    public long countUnread(long userId) {
        return notificationMapper.countUnread(userId);
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void markRead(long userId, long notificationId) {
        notificationMapper.markRead(userId, notificationId, new Date());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void markAllRead(long userId) {
        notificationMapper.markAllRead(userId, new Date());
    }
}

