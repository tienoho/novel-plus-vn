package com.java2nb.common.security;

import com.java2nb.novel.core.security.CspPolicy;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class ContentSecurityPolicyFilterTest {

    @Test
    void writesReportOnlyHeaderWithNonceOnLegacyAdminServletRuntime() throws Exception {
        ContentSecurityPolicyFilter filter = new ContentSecurityPolicyFilter(true, false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/novel/gamification");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        String nonce = (String) request.getAttribute(CspPolicy.NONCE_ATTRIBUTE);
        assertThat(response.getHeader(CspPolicy.REPORT_ONLY_HEADER))
            .isEqualTo(CspPolicy.value(nonce));
        verify(chain).doFilter(request, response);
    }
}
