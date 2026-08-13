package com.java2nb.common.security;

import com.java2nb.novel.core.security.CspPolicy;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import jakarta.servlet.http.HttpServletRequest;

@ControllerAdvice
public class CspNonceModelAdvice {

    @ModelAttribute(CspPolicy.NONCE_ATTRIBUTE)
    public String cspNonce(HttpServletRequest request) {
        Object value = request.getAttribute(CspPolicy.NONCE_ATTRIBUTE);
        return value instanceof String ? value.toString() : null;
    }
}
