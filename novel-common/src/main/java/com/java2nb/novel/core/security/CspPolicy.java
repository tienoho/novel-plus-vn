package com.java2nb.novel.core.security;

import java.security.SecureRandom;
import java.util.Base64;

public final class CspPolicy {

    public static final String NONCE_ATTRIBUTE = CspNonceDialect.NONCE_VARIABLE;
    public static final String ENFORCE_HEADER = "Content-Security-Policy";
    public static final String REPORT_ONLY_HEADER = "Content-Security-Policy-Report-Only";

    private static final SecureRandom RANDOM = new SecureRandom();

    private CspPolicy() {
    }

    public static String newNonce() {
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String headerName(boolean enforce) {
        return enforce ? ENFORCE_HEADER : REPORT_ONLY_HEADER;
    }

    public static String value(String nonce) {
        if (nonce == null || !nonce.matches("[A-Za-z0-9_-]{20,128}")) {
            throw new IllegalArgumentException("CSP nonce không hợp lệ");
        }
        return "default-src 'self'; "
            + "base-uri 'self'; "
            + "object-src 'none'; "
            + "frame-ancestors 'none'; "
            + "form-action 'self'; "
            + "script-src 'self' 'nonce-" + nonce + "'; "
            + "script-src-attr 'none'; "
            + "style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data: blob: https:; "
            + "font-src 'self' data:; "
            + "connect-src 'self' ws: wss:; "
            + "media-src 'self' https:; "
            + "frame-src 'self' https://sandbox.vnpayment.vn";
    }
}
