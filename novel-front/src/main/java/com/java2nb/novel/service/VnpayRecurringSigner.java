package com.java2nb.novel.service;

import com.java2nb.novel.core.config.VnpayRecurringProperties;
import org.springframework.stereotype.Component;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.SortedMap;
import java.util.TreeMap;

@Component
public class VnpayRecurringSigner {

    private static final String HMAC_SHA_512 = "HmacSHA512";
    private final VnpayRecurringProperties properties;

    public VnpayRecurringSigner(VnpayRecurringProperties properties) {
        this.properties = properties;
    }

    public String signFields(List<?> fields) {
        return sign(fields.stream().map(this::stringValue).reduce((left, right) -> left + "|" + right).orElse(""));
    }

    public boolean verifyFields(List<?> fields, String receivedHash) {
        return verify(signFields(fields), receivedHash);
    }

    public boolean verifyCallback(Map<String, String> parameters) {
        String receivedHash = parameters.get("vnp_secure_hash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }
        SortedMap<String, String> signed = new TreeMap<>();
        parameters.forEach((key, value) -> {
            if (key != null && key.startsWith("vnp_") && !"vnp_secure_hash".equals(key)
                && value != null && !value.isBlank()) {
                signed.put(key, value);
            }
        });
        StringBuilder data = new StringBuilder();
        signed.forEach((key, value) -> {
            if (!data.isEmpty()) {
                data.append('&');
            }
            data.append(encode(key)).append('=').append(encode(value));
        });
        return verify(sign(data.toString()), receivedHash);
    }

    String sign(String data) {
        try {
            Mac mac = Mac.getInstance(HMAC_SHA_512);
            mac.init(new SecretKeySpec(properties.getHashSecret().getBytes(StandardCharsets.UTF_8), HMAC_SHA_512));
            byte[] hash = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder(hash.length * 2);
            for (byte value : hash) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException("Không thể tạo chữ ký VNPAY Recurring", exception);
        }
    }

    private boolean verify(String expectedHash, String receivedHash) {
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(expectedHash.getBytes(StandardCharsets.US_ASCII),
            receivedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
