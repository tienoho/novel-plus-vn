package com.java2nb.novel.core.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.java2nb.novel.core.utils.AuthCookieService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CsrfFilterTest {

    private final CsrfFilter filter = new CsrfFilter();

    @Test
    void blocksAuthenticatedWriteWithoutMatchingToken() throws Exception {
        MockHttpServletRequest request = request("POST");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(chain);
    }

    @Test
    void acceptsAuthenticatedWriteWithMatchingToken() throws Exception {
        MockHttpServletRequest request = request("POST");
        request.setCookies(new Cookie(AuthCookieService.ACCESS_COOKIE, "access"),
            new Cookie(AuthCookieService.CSRF_COOKIE, "csrf-value"));
        request.addHeader("X-XSRF-TOKEN", "csrf-value");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    @Test
    void doesNotRequireCsrfForProviderWebhookWithoutUserSession() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pay/vietqr/webhook");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        verify(chain).doFilter(request, response);
    }

    private MockHttpServletRequest request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/user/updateUserInfo");
        request.setCookies(new Cookie(AuthCookieService.ACCESS_COOKIE, "access"));
        return request;
    }
}
