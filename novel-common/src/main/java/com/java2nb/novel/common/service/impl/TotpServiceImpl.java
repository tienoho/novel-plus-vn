package com.java2nb.novel.common.service.impl;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java2nb.novel.common.dao.User2faDao;
import com.java2nb.novel.common.entity.User2faDO;
import com.java2nb.novel.common.service.TotpService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class TotpServiceImpl implements TotpService {

    private final User2faDao user2faDao;
    private static final String PRE_AUTH_SECRET = "KhoiThu_2FA_PreAuth_Key_2026";
    private static final String BASE32_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String generateSecretKey() {
        byte[] buffer = new byte[20]; // 160-bit secret
        new SecureRandom().nextBytes(buffer);
        return base32Encode(buffer);
    }

    @Override
    public String getQrCodeUri(String username, String secretKey) {
        String issuer = "KhoiThu";
        String account = StringUtils.defaultIfBlank(username, "User");
        return String.format("otpauth://totp/%s:%s?secret=%s&issuer=%s", issuer, account, secretKey, issuer);
    }

    @Override
    public boolean verifyTotp(String secretKey, String code) {
        if (StringUtils.isBlank(secretKey) || StringUtils.isBlank(code) || code.length() != 6) {
            return false;
        }

        long currentWindow = System.currentTimeMillis() / 1000L / 30L;
        // Check window -1, 0, +1 for clock skew tolerance
        for (int i = -1; i <= 1; i++) {
            String generatedCode = generateTotpCode(secretKey, currentWindow + i);
            if (code.equals(generatedCode)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public List<String> generateBackupCodes() {
        List<String> codes = new ArrayList<>();
        SecureRandom random = new SecureRandom();
        for (int i = 0; i < 8; i++) {
            StringBuilder sb = new StringBuilder();
            for (int j = 0; j < 8; j++) {
                int idx = random.nextInt(36);
                if (idx < 10)
                    sb.append(idx);
                else
                    sb.append((char) ('a' + idx - 10));
            }
            codes.add(sb.toString());
        }
        return codes;
    }

    @Override
    public boolean is2faEnabled(Long userId) {
        if (userId == null)
            return false;
        User2faDO user2fa = user2faDao.selectByUserId(userId);
        return user2fa != null && Boolean.TRUE.equals(user2fa.getIsEnabled());
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public User2faDO setup2fa(Long userId, String username) {
        User2faDO existing = user2faDao.selectByUserId(userId);
        String secretKey = generateSecretKey();

        if (existing != null) {
            existing.setSecretKeyCiphertext(secretKey);
            existing.setIsEnabled(false);
            user2faDao.updateStatusAndBackupCodes(existing);
            return existing;
        } else {
            User2faDO new2fa = User2faDO.builder()
                    .userId(userId)
                    .secretKeyCiphertext(secretKey)
                    .isEnabled(false)
                    .build();
            user2faDao.insert(new2fa);
            return new2fa;
        }
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public List<String> enable2fa(Long userId, String code) {
        User2faDO user2fa = user2faDao.selectByUserId(userId);
        if (user2fa == null) {
            throw new IllegalStateException("Yeu cau khoi tao 2FA truoc");
        }

        if (!verifyTotp(user2fa.getSecretKeyCiphertext(), code)) {
            throw new IllegalArgumentException("Ma 2FA khong chinh xac");
        }

        List<String> plainBackupCodes = generateBackupCodes();
        List<String> hashedBackupCodes = new ArrayList<>();
        for (String c : plainBackupCodes) {
            hashedBackupCodes.add(hashSha256(c));
        }

        user2fa.setIsEnabled(true);
        user2fa.setEnabledAt(new Date());
        try {
            user2fa.setBackupCodesJson(OBJECT_MAPPER.writeValueAsString(hashedBackupCodes));
        } catch (Exception e) {
            log.error("Error serializing backup codes", e);
        }

        user2faDao.updateStatusAndBackupCodes(user2fa);
        log.info("2FA enabled for userId: {}", userId);
        return plainBackupCodes;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean disable2fa(Long userId, String code) {
        User2faDO user2fa = user2faDao.selectByUserId(userId);
        if (user2fa == null || !Boolean.TRUE.equals(user2fa.getIsEnabled())) {
            return true;
        }

        if (!verify2faOrBackupCode(userId, code)) {
            throw new IllegalArgumentException("Ma 2FA hoac ma du phong khong hop le");
        }

        user2fa.setIsEnabled(false);
        user2fa.setBackupCodesJson(null);
        user2faDao.updateStatusAndBackupCodes(user2fa);
        log.info("2FA disabled for userId: {}", userId);
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public boolean verify2faOrBackupCode(Long userId, String code) {
        User2faDO user2fa = user2faDao.selectByUserId(userId);
        if (user2fa == null || !Boolean.TRUE.equals(user2fa.getIsEnabled())) {
            return true;
        }

        // Try TOTP code first
        if (verifyTotp(user2fa.getSecretKeyCiphertext(), code)) {
            user2fa.setLastVerifiedAt(new Date());
            user2faDao.updateStatusAndBackupCodes(user2fa);
            return true;
        }

        // Try Backup code match
        if (StringUtils.isNotBlank(user2fa.getBackupCodesJson())) {
            try {
                List<String> hashedCodes = OBJECT_MAPPER.readValue(user2fa.getBackupCodesJson(),
                        new TypeReference<List<String>>() {
                        });
                String incomingHash = hashSha256(code.trim().toLowerCase());

                if (hashedCodes != null && hashedCodes.contains(incomingHash)) {
                    hashedCodes.remove(incomingHash);
                    user2fa.setBackupCodesJson(OBJECT_MAPPER.writeValueAsString(hashedCodes));
                    user2fa.setLastVerifiedAt(new Date());
                    user2faDao.updateStatusAndBackupCodes(user2fa);
                    log.info("Consumed 2FA backup code for userId: {}", userId);
                    return true;
                }
            } catch (Exception e) {
                log.error("Error reading backup codes JSON", e);
            }
        }
        return false;
    }

    @Override
    public String createPreAuthToken(Long userId, String username) {
        long exp = System.currentTimeMillis() + (5 * 60 * 1000L); // 5 minutes validity
        String payload = userId + ":" + (username != null ? username : "") + ":" + exp;
        String sig = hashSha256(payload + "|" + PRE_AUTH_SECRET);
        return Base64.getUrlEncoder().encodeToString((payload + ":" + sig).getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public Long verifyPreAuthToken(String preAuthToken) {
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(preAuthToken), StandardCharsets.UTF_8);
            String[] parts = decoded.split(":");
            if (parts.length != 4)
                return null;

            Long userId = Long.parseLong(parts[0]);
            String username = parts[1];
            long exp = Long.parseLong(parts[2]);
            String expectedSig = parts[3];

            if (System.currentTimeMillis() > exp)
                return null;

            String payload = userId + ":" + username + ":" + exp;
            String computedSig = hashSha256(payload + "|" + PRE_AUTH_SECRET);

            if (computedSig.equals(expectedSig)) {
                return userId;
            }
        } catch (Exception e) {
            log.error("Failed to verify pre-auth token", e);
        }
        return null;
    }

    private String generateTotpCode(String secretKey, long timeWindow) {
        try {
            byte[] keyBytes = base32Decode(secretKey);
            byte[] data = ByteBuffer.allocate(8).putLong(timeWindow).array();

            Mac mac = Mac.getInstance("HmacSHA1");
            mac.init(new SecretKeySpec(keyBytes, "HmacSHA1"));
            byte[] hash = mac.doFinal(data);

            int offset = hash[hash.length - 1] & 0xF;
            int binary = ((hash[offset] & 0x7F) << 24) |
                    ((hash[offset + 1] & 0xFF) << 16) |
                    ((hash[offset + 2] & 0xFF) << 8) |
                    (hash[offset + 3] & 0xFF);

            int otp = binary % 1_000_000;
            return String.format("%06d", otp);
        } catch (Exception e) {
            log.error("Error generating TOTP code", e);
            return "";
        }
    }

    private String base32Encode(byte[] data) {
        StringBuilder result = new StringBuilder();
        int buffer = data[0];
        int next = 1;
        int bitsLeft = 8;
        while (bitsLeft > 0 || next < data.length) {
            if (bitsLeft < 5) {
                if (next < data.length) {
                    buffer = (buffer << 8) | (data[next++] & 0xFF);
                    bitsLeft += 8;
                } else {
                    int pad = 5 - bitsLeft;
                    buffer <<= pad;
                    bitsLeft += pad;
                }
            }
            int index = 0x1F & (buffer >> (bitsLeft - 5));
            bitsLeft -= 5;
            result.append(BASE32_CHARS.charAt(index));
        }
        return result.toString();
    }

    private byte[] base32Decode(String base32) {
        String clean = base32.toUpperCase().replaceAll("[^A-Z2-7]", "");
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (char c : clean.toCharArray()) {
            buffer = (buffer << 5) | BASE32_CHARS.indexOf(c);
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xFF);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private String hashSha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1)
                    hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return "HASH_ERROR";
        }
    }
}
