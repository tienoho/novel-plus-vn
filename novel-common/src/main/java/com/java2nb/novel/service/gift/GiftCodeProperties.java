package com.java2nb.novel.service.gift;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

@Data
@Component
@ConfigurationProperties(prefix = "novel.gift-code")
public class GiftCodeProperties {
    private boolean enabled;
    private String hmacKeyId = "legacy-v1";
    private String hmacSecret = "disabled";
    private String hmacVerificationKeys = "";
    private String policyVersion = "v1";

    public boolean isReady() {
        return enabled && hasValidKeyRing()
            && policyVersion != null && !policyVersion.isBlank() && policyVersion.length() <= 32;
    }

    public boolean hasValidKeyRing() {
        try {
            configuredKeyBytes();
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    public Map<String, byte[]> configuredKeyBytes() {
        if (!validKeyId(hmacKeyId) || hmacSecret == null || hmacSecret.length() < 32) {
            throw new IllegalArgumentException("Khóa HMAC active của mã quà không hợp lệ");
        }
        LinkedHashMap<String, byte[]> keys = new LinkedHashMap<>();
        keys.put(hmacKeyId, hmacSecret.getBytes(StandardCharsets.UTF_8));
        String configured = hmacVerificationKeys == null ? "" : hmacVerificationKeys.trim();
        if (!configured.isEmpty()) {
            for (String entry : configured.split(";")) {
                String normalized = entry.trim();
                int separator = normalized.indexOf(':');
                if (separator <= 0 || separator == normalized.length() - 1) {
                    throw new IllegalArgumentException("Khóa HMAC xác minh mã quà không hợp lệ");
                }
                String keyId = normalized.substring(0, separator).trim();
                byte[] secret;
                try {
                    secret = Base64.getDecoder().decode(
                        normalized.substring(separator + 1).trim());
                } catch (IllegalArgumentException exception) {
                    throw new IllegalArgumentException(
                        "Secret HMAC xác minh mã quà phải là Base64", exception);
                }
                if (!validKeyId(keyId) || secret.length < 32 || keys.putIfAbsent(keyId, secret) != null) {
                    throw new IllegalArgumentException("Key ring HMAC mã quà bị trùng hoặc không hợp lệ");
                }
            }
        }
        if (keys.size() > 8) {
            throw new IllegalArgumentException("Key ring HMAC mã quà vượt quá 8 khóa");
        }
        LinkedHashMap<String, byte[]> copies = new LinkedHashMap<>();
        keys.forEach((keyId, secret) -> copies.put(keyId, secret.clone()));
        return java.util.Collections.unmodifiableMap(copies);
    }

    private boolean validKeyId(String keyId) {
        return keyId != null && keyId.matches("[A-Za-z0-9._-]{1,32}");
    }
}
