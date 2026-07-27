package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.service.notification.NotificationService;
import io.github.xxyopen.model.resp.RestResult;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("user")
@RequiredArgsConstructor
public class UserNotificationController extends BaseController {
    private final NotificationService notificationService;

    @GetMapping("notifications")
    public RestResult<?> list(@RequestParam(value = "curr", defaultValue = "1") int page,
                              @RequestParam(value = "limit", defaultValue = "20") int pageSize,
                              HttpServletRequest request) {
        UserDetails user = getUserDetails(request);
        return user == null ? RestResult.fail(ResponseStatus.NO_LOGIN)
            : RestResult.ok(notificationService.listNotifications(user.getId(), page, pageSize));
    }

    @GetMapping("notifications/unread-count")
    public RestResult<?> unreadCount(HttpServletRequest request) {
        UserDetails user = getUserDetails(request);
        return user == null ? RestResult.fail(ResponseStatus.NO_LOGIN)
            : RestResult.ok(Map.of("count", notificationService.countUnread(user.getId())));
    }

    @PostMapping("notifications/{notificationId}/read")
    public RestResult<?> markRead(@PathVariable long notificationId, HttpServletRequest request) {
        UserDetails user = getUserDetails(request);
        if (user == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        notificationService.markRead(user.getId(), notificationId);
        return RestResult.ok();
    }

    @PostMapping("notifications/read-all")
    public RestResult<?> markAllRead(HttpServletRequest request) {
        UserDetails user = getUserDetails(request);
        if (user == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        notificationService.markAllRead(user.getId());
        return RestResult.ok();
    }

    @GetMapping("follows/authors/{authorId}")
    public RestResult<?> followsAuthor(@PathVariable long authorId, HttpServletRequest request) {
        UserDetails user = getUserDetails(request);
        return user == null ? RestResult.fail(ResponseStatus.NO_LOGIN)
            : RestResult.ok(notificationService.isFollowingAuthor(user.getId(), authorId));
    }

    @PostMapping("follows/authors/{authorId}")
    public RestResult<?> followAuthor(@PathVariable long authorId, HttpServletRequest request) {
        UserDetails user = getUserDetails(request);
        if (user == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        notificationService.followAuthor(user.getId(), authorId);
        return RestResult.ok();
    }

    @DeleteMapping("follows/authors/{authorId}")
    public RestResult<?> unfollowAuthor(@PathVariable long authorId, HttpServletRequest request) {
        UserDetails user = getUserDetails(request);
        if (user == null) {
            return RestResult.fail(ResponseStatus.NO_LOGIN);
        }
        notificationService.unfollowAuthor(user.getId(), authorId);
        return RestResult.ok();
    }
}

