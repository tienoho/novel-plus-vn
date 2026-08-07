package com.java2nb.novel.core.utils;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Component
public class DeviceCookieService {

    public static final String COOKIE_NAME = "NovelDevice";

    @Value("${auth.cookie.secure:false}")
    private boolean secure;

    public String resolve(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (COOKIE_NAME.equals(cookie.getName())) {
                    try {
                        return UUID.fromString(cookie.getValue()).toString();
                    } catch (IllegalArgumentException ignored) {
                        break;
                    }
                }
            }
        }
        String deviceId = UUID.randomUUID().toString();
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, deviceId)
            .httpOnly(true)
            .secure(secure)
            .sameSite("Lax")
            .path("/")
            .maxAge(Duration.ofDays(365))
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return deviceId;
    }
}
