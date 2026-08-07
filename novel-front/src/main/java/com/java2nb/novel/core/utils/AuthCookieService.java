package com.java2nb.novel.core.utils;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieService {

    public static final String ACCESS_COOKIE = "NovelAccess";
    public static final String REFRESH_COOKIE = "NovelRefresh";
    public static final String SESSION_HINT_COOKIE = "NovelSession";
    public static final String CSRF_COOKIE = "XSRF-TOKEN";

    @Value("${auth.cookie.secure:false}")
    private boolean secure;

    @Value("${jwt.access-expiration:900}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration:604800}")
    private long refreshExpiration;

    public void write(HttpServletResponse response, JwtTokenUtil.TokenPair tokens) {
        add(response, ACCESS_COOKIE, tokens.accessToken(), true, "Lax", accessExpiration);
        add(response, REFRESH_COOKIE, tokens.refreshToken(), true, "Strict", refreshExpiration);
        add(response, SESSION_HINT_COOKIE, "1", false, "Lax", refreshExpiration);
        add(response, CSRF_COOKIE, UUID.randomUUID().toString(), false, "Lax", refreshExpiration);
    }

    public void clear(HttpServletResponse response) {
        add(response, ACCESS_COOKIE, "", true, "Lax", 0);
        add(response, REFRESH_COOKIE, "", true, "Strict", 0);
        add(response, SESSION_HINT_COOKIE, "", false, "Lax", 0);
        add(response, CSRF_COOKIE, "", false, "Lax", 0);
    }

    private void add(HttpServletResponse response, String name, String value, boolean httpOnly,
                     String sameSite, long maxAgeSeconds) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
            .httpOnly(httpOnly)
            .secure(secure)
            .sameSite(sameSite)
            .path("/")
            .maxAge(Duration.ofSeconds(maxAgeSeconds))
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
