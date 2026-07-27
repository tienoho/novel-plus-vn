package com.java2nb.novel.controller;

import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;

class UserNotificationControllerMappingTest {

    @Test
    void inboxAndUnreadEndpointsRemainMapped() throws Exception {
        Method list = UserNotificationController.class.getDeclaredMethod(
            "list", int.class, int.class, jakarta.servlet.http.HttpServletRequest.class);
        Method unread = UserNotificationController.class.getDeclaredMethod(
            "unreadCount", jakarta.servlet.http.HttpServletRequest.class);

        assertThat(list.getAnnotation(GetMapping.class).value()).containsExactly("notifications");
        assertThat(unread.getAnnotation(GetMapping.class).value()).containsExactly("notifications/unread-count");
    }

    @Test
    void markAllReadEndpointUsesPost() throws Exception {
        Method method = UserNotificationController.class.getDeclaredMethod(
            "markAllRead", jakarta.servlet.http.HttpServletRequest.class);
        assertThat(method.getAnnotation(PostMapping.class).value()).containsExactly("notifications/read-all");
    }
}

