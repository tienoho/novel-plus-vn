package com.java2nb.novel.core.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ContentSecurityPolicyFilterTest {

    @Test
    void writesReportOnlyHeaderAndExposesSameNonceToThymeleaf() throws Exception {
        ContentSecurityPolicyFilter filter = new ContentSecurityPolicyFilter(true, false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        String nonce = (String) request.getAttribute(CspPolicy.NONCE_ATTRIBUTE);
        assertThat(nonce).matches("[A-Za-z0-9_-]{24}");
        assertThat(response.getHeader(CspPolicy.REPORT_ONLY_HEADER))
            .isEqualTo(CspPolicy.value(nonce));
        assertThat(response.getHeader(CspPolicy.ENFORCE_HEADER)).isNull();
        verify(chain).doFilter(request, response);
    }

    @Test
    void usesEnforceHeaderOnlyWhenExplicitlyEnabled() throws Exception {
        ContentSecurityPolicyFilter filter = new ContentSecurityPolicyFilter(true, true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        String nonce = (String) request.getAttribute(CspPolicy.NONCE_ATTRIBUTE);
        assertThat(response.getHeader(CspPolicy.ENFORCE_HEADER))
            .isEqualTo(CspPolicy.value(nonce));
        assertThat(response.getHeader(CspPolicy.REPORT_ONLY_HEADER)).isNull();
    }
}
