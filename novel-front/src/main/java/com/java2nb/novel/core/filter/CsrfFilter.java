package com.java2nb.novel.core.filter;

import com.java2nb.novel.core.utils.AuthCookieService;
import com.java2nb.novel.core.utils.CookieUtil;
import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Set;

public class CsrfFilter implements Filter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        if (SAFE_METHODS.contains(httpRequest.getMethod()) || !hasAuthenticatedSession(httpRequest)) {
            chain.doFilter(request, response);
            return;
        }

        String cookieToken = CookieUtil.getCookie(httpRequest, AuthCookieService.CSRF_COOKIE);
        String headerToken = httpRequest.getHeader("X-XSRF-TOKEN");
        if (!constantTimeEquals(cookieToken, headerToken)) {
            httpResponse.sendError(HttpServletResponse.SC_FORBIDDEN, "CSRF token không hợp lệ");
            return;
        }
        chain.doFilter(request, response);
    }

    private boolean hasAuthenticatedSession(HttpServletRequest request) {
        return CookieUtil.getCookie(request, AuthCookieService.ACCESS_COOKIE) != null;
    }

    private boolean constantTimeEquals(String left, String right) {
        if (left == null || right == null) {
            return false;
        }
        return MessageDigest.isEqual(
            left.getBytes(StandardCharsets.UTF_8), right.getBytes(StandardCharsets.UTF_8));
    }
}
