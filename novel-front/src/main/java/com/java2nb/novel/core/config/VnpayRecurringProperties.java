package com.java2nb.novel.core.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.time.DateTimeException;
import java.time.ZoneId;

@Data
@Component
@ConfigurationProperties(prefix = "vnpay.recurring")
public class VnpayRecurringProperties {

    private boolean enabled;
    private String baseUrl = "https://sandbox.vnpayment.vn";
    private String clientId = "disabled";
    private String username = "disabled";
    private String password = "disabled";
    private String clientSecret = "disabled";
    private String tmnCode = "disabled";
    private String hashSecret = "disabled";
    private String payUrl = "https://sandbox.vnpayment.vn/recurring-payment/pay";
    private String returnUrl = "http://localhost:8083/payment/vnpay-recurring/return";
    private String cancelUrl = "http://localhost:8083/payment/vnpay-recurring/cancel";
    private String resultRedirectUrl = "/pay";
    private String orderType = "other";
    private String version = "2.1.0";
    private String locale = "vn";
    private String timeZone = "Asia/Ho_Chi_Minh";
    private int connectTimeoutSeconds = 5;
    private int requestTimeoutSeconds = 15;

    public boolean isConfigured() {
        return enabled && isHttpUrl(baseUrl) && isHttpUrl(payUrl) && isHttpUrl(returnUrl)
            && isHttpUrl(cancelUrl) && hasText(resultRedirectUrl)
            && matches(clientId, "[A-Za-z0-9]{1,20}")
            && matches(username, "[A-Za-z0-9]{1,50}")
            && hasLength(password, 8, 256) && hasLength(clientSecret, 1, 256)
            && matches(tmnCode, "[A-Za-z0-9]{8}") && hasLength(hashSecret, 32, 256)
            && hasLength(orderType, 1, 100) && "2.1.0".equals(version)
            && ("vn".equals(locale) || "en".equals(locale)) && hasValidTimeZone()
            && connectTimeoutSeconds >= 1 && connectTimeoutSeconds <= 30
            && requestTimeoutSeconds >= connectTimeoutSeconds && requestTimeoutSeconds <= 60;
    }

    public URI endpoint(String path) {
        String normalizedBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        return URI.create(normalizedBase + path);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank() && !"disabled".equalsIgnoreCase(value);
    }

    private boolean hasLength(String value, int min, int max) {
        return hasText(value) && value.length() >= min && value.length() <= max;
    }

    private boolean matches(String value, String pattern) {
        return hasText(value) && value.matches(pattern);
    }

    private boolean isHttpUrl(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            URI uri = URI.create(value);
            return uri.getHost() != null && ("http".equalsIgnoreCase(uri.getScheme())
                || "https".equalsIgnoreCase(uri.getScheme()));
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private boolean hasValidTimeZone() {
        try {
            ZoneId.of(timeZone);
            return true;
        } catch (DateTimeException | NullPointerException exception) {
            return false;
        }
    }
}
