package com.java2nb.novel.mapper;

import com.java2nb.novel.service.notification.ChapterPublishEventRow;
import com.java2nb.novel.service.notification.UserNotificationRow;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

public interface NotificationMapper {
    List<Long> listPendingEventIds(@Param("now") Date now, @Param("limit") int limit);
    int claimEvent(@Param("id") long id);
    ChapterPublishEventRow selectClaimedEvent(@Param("id") long id);
    int fanOut(ChapterPublishEventRow event);
    int markProcessed(@Param("id") long id, @Param("now") Date now);
    int recordFailure(@Param("id") long id, @Param("error") String error, @Param("now") Date now);

    int authorExists(@Param("authorId") long authorId);
    int followAuthor(@Param("userId") long userId, @Param("authorId") long authorId);
    int unfollowAuthor(@Param("userId") long userId, @Param("authorId") long authorId);
    int isFollowingAuthor(@Param("userId") long userId, @Param("authorId") long authorId);

    List<UserNotificationRow> listNotifications(@Param("userId") long userId);
    long countUnread(@Param("userId") long userId);
    int markRead(@Param("userId") long userId, @Param("notificationId") long notificationId,
                 @Param("now") Date now);
    int markAllRead(@Param("userId") long userId, @Param("now") Date now);
}

