package com.java2nb.novel.service.finance;

import com.java2nb.novel.core.config.PiiCryptoProperties;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

@Service
public class PiiCryptoService {

    private static final String VERSION = "v1";
    private static final int KEY_LENGTH = 32;
    private static final int IV_LENGTH = 12;
    private static final int GCM_TAG_BITS = 128;

    private final byte[] key;
    private final SecureRandom secureRandom = new SecureRandom();

    public PiiCryptoService(PiiCryptoProperties properties) {
        this.key = decodeKey(properties.getEncryptionKey());
    }

    public boolean isConfigured() {
        return key != null;
    }

    public String encrypt(String plaintext) {
        if (plaintext == null || plaintext.isBlank()) {
            return null;
        }
        byte[] configuredKey = requireKey();
        byte[] iv = new byte[IV_LENGTH];
        secureRandom.nextBytes(iv);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(configuredKey, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(VERSION.getBytes(StandardCharsets.US_ASCII));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return VERSION + ':' + Base64.getEncoder().encodeToString(ByteBuffer.allocate(iv.length + ciphertext.length)
                .put(iv)
                .put(ciphertext)
                .array());
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Không thể mã hóa dữ liệu định danh", exception);
        }
    }

    public String decrypt(String encryptedValue) {
        if (encryptedValue == null || encryptedValue.isBlank()) {
            return null;
        }
        byte[] configuredKey = requireKey();
        String[] parts = encryptedValue.split(":", 2);
        if (parts.length != 2 || !VERSION.equals(parts[0])) {
            throw new IllegalArgumentException("Phiên bản ciphertext không được hỗ trợ");
        }
        byte[] payload = Base64.getDecoder().decode(parts[1]);
        if (payload.length <= IV_LENGTH) {
            throw new IllegalArgumentException("Ciphertext không hợp lệ");
        }
        byte[] iv = new byte[IV_LENGTH];
        byte[] ciphertext = new byte[payload.length - IV_LENGTH];
        System.arraycopy(payload, 0, iv, 0, iv.length);
        System.arraycopy(payload, iv.length, ciphertext, 0, ciphertext.length);
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(configuredKey, "AES"),
                new GCMParameterSpec(GCM_TAG_BITS, iv));
            cipher.updateAAD(VERSION.getBytes(StandardCharsets.US_ASCII));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException exception) {
            throw new IllegalArgumentException("Không thể giải mã dữ liệu định danh", exception);
        }
    }

    public String deterministicHash(String normalizedValue) {
        if (normalizedValue == null || normalizedValue.isBlank()) {
            return null;
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(requireKey(), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("Không thể tạo HMAC dữ liệu định danh", exception);
        }
    }

    private byte[] requireKey() {
        if (key == null) {
            throw new PiiEncryptionUnavailableException();
        }
        return key;
    }

    private byte[] decodeKey(String encodedKey) {
        if (encodedKey == null || encodedKey.isBlank() || "disabled".equalsIgnoreCase(encodedKey)) {
            return null;
        }
        try {
            byte[] decoded = Base64.getDecoder().decode(encodedKey.trim());
            return decoded.length == KEY_LENGTH ? decoded : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }
}
