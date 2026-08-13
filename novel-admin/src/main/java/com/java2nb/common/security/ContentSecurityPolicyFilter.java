package com.java2nb.common.security;

import com.java2nb.novel.core.security.CspPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ContentSecurityPolicyFilter extends OncePerRequestFilter {

    private final boolean enabled;
    private final boolean enforce;

    public ContentSecurityPolicyFilter(
        @Value("${security.csp.enabled:true}") boolean enabled,
        @Value("${security.csp.enforce:true}") boolean enforce) {
        this.enabled = enabled;
        this.enforce = enforce;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain)
        throws ServletException, IOException {
        if (enabled) {
            String nonce = CspPolicy.newNonce();
            request.setAttribute(CspPolicy.NONCE_ATTRIBUTE, nonce);
            response.setHeader(CspPolicy.headerName(enforce), CspPolicy.value(nonce));
        }
        filterChain.doFilter(request, response);
    }
}
