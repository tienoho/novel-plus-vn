package com.java2nb.novel.core.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Tiện ích băm SHA-256 phục vụ xác thực nội dung và phát hiện trùng lặp
 */
public class ContentHashUtil {

    private ContentHashUtil() {
    }

    public static String sha256Hex(String content) {
        if (content == null) {
            return "";
        }
        return sha256Hex(content.getBytes(StandardCharsets.UTF_8));
    }

    public static String sha256Hex(byte[] data) {
        if (data == null || data.length == 0) {
            return "";
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 algorithm not available", e);
        }
    }
}
