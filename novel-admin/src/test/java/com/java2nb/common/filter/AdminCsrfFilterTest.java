package com.java2nb.common.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Field;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class AdminCsrfFilterTest {

    private AdminCsrfFilter filter;

    @BeforeEach
    void setUp() throws Exception {
        filter = new AdminCsrfFilter();
        Field secure = AdminCsrfFilter.class.getDeclaredField("secureCookie");
        secure.setAccessible(true);
        secure.set(filter, true);
    }

    @Test
    void safeRequestReceivesJavascriptReadableSecureCookie() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getHeader("Set-Cookie"))
            .contains("XSRF-TOKEN=", "Path=/", "SameSite=Lax", "Secure")
            .doesNotContain("HttpOnly");
        verify(chain).doFilter(request, response);
    }

    @Test
    void blocksUnsafeRequestWithoutMatchingHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/login");
        request.setCookies(new Cookie(AdminCsrfFilter.COOKIE_NAME, "known-token"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verify(chain, never()).doFilter(request, response);
    }

    @Test
    void acceptsUnsafeRequestWithMatchingCookieAndHeader() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("DELETE", "/sys/menu/remove");
        request.setCookies(new Cookie(AdminCsrfFilter.COOKIE_NAME, "known-token"));
        request.addHeader(AdminCsrfFilter.HEADER_NAME, "known-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
    }
}
