package com.java2nb.common.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class AdminPiiCryptoService {

    private static final String VERSION = "v1";
    private static final int IV_LENGTH = 12;

    private final byte[] key;

    public AdminPiiCryptoService(@Value("${security.pii.encryption-key:disabled}") String encodedKey) {
        this.key = decodeKey(encodedKey);
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isEmpty()) {
            return null;
        }
        if (key == null) {
            throw new IllegalStateException("Chưa cấu hình khóa giải mã dữ liệu KYC");
        }
        String[] parts = encryptedValue.split(":", 2);
        if (parts.length != 2 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Phiên bản ciphertext KYC không được hỗ trợ");
        }
        byte[] payload = Base64.getDecoder().decode(parts[1]);
        if (payload.length <= IV_LENGTH) {
            throw new IllegalArgumentException("Ciphertext KYC không hợp lệ");
        }
        byte[] iv = new byte[IV_LENGTH];
        byte[] ciphertext = new byte[payload.length - IV_LENGTH];
        System.arraycopy(payload, 0, iv, 0, iv.length);
        System.arraycopy(payload, iv.length, ciphertext, 0, ciphertext.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, iv));
            cipher.updateAAD(VERSION.getBytes(StandardCharsets.US_ASCII));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Không thể giải mã dữ liệu KYC", exception);
        }
    }

    private byte[] decodeKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isEmpty() || "disabled".equalsIgnoreCase(encodedKey)) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey.trim());
            return decoded.length == 32 ? decoded : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
