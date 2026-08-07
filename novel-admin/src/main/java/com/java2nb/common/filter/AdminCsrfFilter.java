package com.java2nb.common.filter;

import com.java2nb.common.utils.Messages;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;
import java.util.UUID;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Bảo vệ request ghi của admin bằng mẫu double-submit cookie.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AdminCsrfFilter extends OncePerRequestFilter {

    public static final String COOKIE_NAME = "XSRF-TOKEN";
    public static final String HEADER_NAME = "X-XSRF-TOKEN";
    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Value("${security.csrf.cookie-secure:false}")
    private boolean secureCookie;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
        throws ServletException, IOException {
        String cookieToken = readCookie(request);
        if (cookieToken == null || cookieToken.isBlank()) {
            cookieToken = UUID.randomUUID().toString();
            writeCookie(response, cookieToken);
        }

        if (!SAFE_METHODS.contains(request.getMethod().toUpperCase())) {
            String headerToken = request.getHeader(HEADER_NAME);
            if (!constantTimeEquals(cookieToken, headerToken)) {
                response.sendError(
                    HttpServletResponse.SC_FORBIDDEN,
                    Messages.getDefault("error.csrf.invalid"));
                return;
            }
        }
        filterChain.doFilter(request, response);
    }

    private String readCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        for (Cookie cookie : cookies) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    private boolean constantTimeEquals(String expected, String actual) {
        if (expected == null || actual == null) {
            return false;
        }
        return MessageDigest.isEqual(
            expected.getBytes(StandardCharsets.UTF_8),
            actual.getBytes(StandardCharsets.UTF_8));
    }

    private void writeCookie(HttpServletResponse response, String token) {
        StringBuilder value = new StringBuilder(COOKIE_NAME)
            .append('=').append(token)
            .append("; Path=/; SameSite=Lax");
        if (secureCookie) {
            value.append("; Secure");
        }
        response.addHeader("Set-Cookie", value.toString());
    }
}
