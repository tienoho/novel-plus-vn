package com.java2nb.novel.core.security;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ContentSecurityPolicyFilterTest {

    @Test
    void writesReportOnlyPolicyCompatibleWithCrawlerWebSocket() throws Exception {
        ContentSecurityPolicyFilter filter = new ContentSecurityPolicyFilter(true, false);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/crawl/crawlSingleTask/list");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        String nonce = (String) request.getAttribute(CspPolicy.NONCE_ATTRIBUTE);
        assertThat(response.getHeader(CspPolicy.REPORT_ONLY_HEADER))
            .isEqualTo(CspPolicy.value(nonce))
            .contains("connect-src 'self' ws: wss:");
    }
}
