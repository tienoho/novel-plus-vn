package com.java2nb.novel.service;

import com.java2nb.novel.core.config.VnpayProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Locale;
import java.util.Date;
import java.util.Objects;
import java.util.SortedMap;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
public class VnpayService {

    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final String HMAC_SHA_512 = "HmacSHA512";

    private final VnpayProperties properties;

    public String createPaymentUrl(long outTradeNo, int amountVnd, String ipAddress, Date createTime) {
        LocalDateTime now = LocalDateTime.ofInstant(Objects.requireNonNull(createTime).toInstant(),
            ZoneId.of(properties.getTimeZone()));
        SortedMap<String, String> params = new TreeMap<>();
        params.put("vnp_Version", properties.getVersion());
        params.put("vnp_Command", "pay");
        params.put("vnp_TmnCode", properties.getTmnCode());
        params.put("vnp_Amount", String.valueOf(Math.multiplyExact(amountVnd, 100)));
        params.put("vnp_CurrCode", "VND");
        params.put("vnp_TxnRef", String.valueOf(outTradeNo));
        params.put("vnp_OrderInfo", "Nap Xu Novel Plus " + outTradeNo);
        params.put("vnp_OrderType", properties.getOrderType());
        params.put("vnp_Locale", properties.getLocale());
        params.put("vnp_ReturnUrl", properties.getReturnUrl());
        params.put("vnp_IpAddr", normalizeIp(ipAddress));
        params.put("vnp_CreateDate", now.format(VNPAY_DATE_FORMAT));
        params.put("vnp_ExpireDate", now.plusMinutes(15).format(VNPAY_DATE_FORMAT));

        String query = buildQuery(params);
        return properties.getPayUrl() + "?" + query + "&vnp_SecureHash=" + sign(query);
    }

    public boolean verifySignature(Map<String, String> responseParams) {
        if (!properties.isConfigured() || !properties.getTmnCode().equals(responseParams.get("vnp_TmnCode"))) {
            return false;
        }
        String receivedHash = responseParams.get("vnp_SecureHash");
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }
        SortedMap<String, String> signedParams = new TreeMap<>();
        responseParams.forEach((key, value) -> {
            if (key.startsWith("vnp_") && !"vnp_SecureHash".equals(key) && !"vnp_SecureHashType".equals(key)
                && value != null && !value.isBlank()) {
                signedParams.put(key, value);
            }
        });
        return verifyHash(buildQuery(signedParams), receivedHash);
    }

    boolean verifyHash(String data, String receivedHash) {
        if (receivedHash == null || receivedHash.isBlank()) {
            return false;
        }
        byte[] expected = sign(data).getBytes(StandardCharsets.US_ASCII);
        byte[] actual = receivedHash.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, actual);
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
            throw new IllegalStateException("Không thể tạo chữ ký VNPAY", exception);
        }
    }

    private String buildQuery(SortedMap<String, String> params) {
        StringBuilder result = new StringBuilder();
        params.forEach((key, value) -> {
            if (value != null && !value.isBlank()) {
                if (!result.isEmpty()) {
                    result.append('&');
                }
                result.append(encode(key)).append('=').append(encode(value));
            }
        });
        return result.toString();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.US_ASCII);
    }

    private String normalizeIp(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank() || "0:0:0:0:0:0:0:1".equals(ipAddress) || "::1".equals(ipAddress)) {
            return "127.0.0.1";
        }
        return ipAddress.trim();
    }
}
