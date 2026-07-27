package com.java2nb.novel.core.utils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;

/**
 * Tiện ích SimHash 64-bit cho việc phát hiện đạo văn và tính khoảng cách Hamming
 */
public class SimHashUtil {

    private static final int HASH_BITS = 64;

    private SimHashUtil() {
    }

    /**
     * Tính mã fingerprint 64-bit SimHash dưới dạng chuỗi nhị phân 64 ký tự ('0' và '1')
     */
    public static String getSimHash(String text) {
        long hashLong = getSimHashLong(text);
        StringBuilder sb = new StringBuilder();
        for (int i = HASH_BITS - 1; i >= 0; i--) {
            sb.append((hashLong >> i) & 1L);
        }
        return sb.toString();
    }

    /**
     * Tính mã 64-bit SimHash dưới dạng số long
     */
    public static long getSimHashLong(String text) {
        if (text == null || text.trim().isEmpty()) {
            return 0L;
        }

        List<String> tokens = tokenize(text);
        if (tokens.isEmpty()) {
            return 0L;
        }

        int[] v = new int[HASH_BITS];
        for (String token : tokens) {
            long tokenHash = hash64(token);
            for (int i = 0; i < HASH_BITS; i++) {
                long bit = (tokenHash >> i) & 1L;
                if (bit == 1) {
                    v[i]++;
                } else {
                    v[i]--;
                }
            }
        }

        long fingerprint = 0L;
        for (int i = 0; i < HASH_BITS; i++) {
            if (v[i] > 0) {
                fingerprint |= (1L << i);
            }
        }
        return fingerprint;
    }

    /**
     * Tính khoảng cách Hamming giữa hai chuỗi SimHash nhị phân hoặc hai số long
     */
    public static int getHammingDistance(long h1, long h2) {
        return Long.bitCount(h1 ^ h2);
    }

    public static int getHammingDistance(String simHash1, String simHash2) {
        if (simHash1 == null || simHash2 == null || simHash1.length() != HASH_BITS || simHash2.length() != HASH_BITS) {
            long l1 = parseSimHashToLong(simHash1);
            long l2 = parseSimHashToLong(simHash2);
            return getHammingDistance(l1, l2);
        }
        long l1 = parseSimHashToLong(simHash1);
        long l2 = parseSimHashToLong(simHash2);
        return getHammingDistance(l1, l2);
    }

    /**
     * Kiểm tra hai nội dung có bị nghi ngờ trùng lặp/đạo văn hay không (ngưỡng mặc định d <= 3)
     */
    public static boolean isSimilar(String simHash1, String simHash2, int threshold) {
        if (simHash1 == null || simHash2 == null) {
            return false;
        }
        return getHammingDistance(simHash1, simHash2) <= threshold;
    }

    public static boolean isSimilar(String text1, String text2) {
        if (text1 == null || text2 == null) {
            return false;
        }
        String h1 = getSimHash(text1);
        String h2 = getSimHash(text2);
        return isSimilar(h1, h2, 15);
    }

    private static long parseSimHashToLong(String simHashStr) {
        if (simHashStr == null || simHashStr.isEmpty()) {
            return 0L;
        }
        if (simHashStr.length() == HASH_BITS) {
            long res = 0L;
            for (int i = 0; i < HASH_BITS; i++) {
                if (simHashStr.charAt(HASH_BITS - 1 - i) == '1') {
                    res |= (1L << i);
                }
            }
            return res;
        }
        try {
            return Long.parseUnsignedLong(simHashStr, 16);
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static List<String> tokenize(String text) {
        List<String> tokens = new ArrayList<>();
        String cleaned = text.toLowerCase().replaceAll("[^\\p{L}\\p{Nd}]+", " ");
        String[] words = cleaned.split("\\s+");

        for (String w : words) {
            if (w.length() >= 1) {
                tokens.add(w);
            }
        }
        // Thêm n-grams (bi-grams) để tăng tính đặc trưng của văn bản
        for (int i = 0; i < words.length - 1; i++) {
            if (!words[i].isEmpty() && !words[i + 1].isEmpty()) {
                tokens.add(words[i] + "_" + words[i + 1]);
            }
        }
        return tokens;
    }

    private static long hash64(String token) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] bytes = md.digest(token.getBytes(StandardCharsets.UTF_8));
            long h = 0;
            for (int i = 0; i < 8; i++) {
                h = (h << 8) | (bytes[i] & 0xFF);
            }
            return h;
        } catch (NoSuchAlgorithmException e) {
            // FNV-1a fallback
            long hash = 0xcbf29ce484222325L;
            byte[] data = token.getBytes(StandardCharsets.UTF_8);
            for (byte b : data) {
                hash ^= (b & 0xff);
                hash *= 0x100000001b3L;
            }
            return hash;
        }
    }
}
