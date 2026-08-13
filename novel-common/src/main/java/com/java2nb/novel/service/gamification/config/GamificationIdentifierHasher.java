package com.java2nb.novel.service.gamification.config;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

@Component
public class GamificationIdentifierHasher {

    private final GamificationBootstrapProperties bootstrap;

    public GamificationIdentifierHasher(GamificationBootstrapProperties bootstrap) {
        this.bootstrap = bootstrap;
    }

    public String hash(String domain, String rawValue, String requiredKeyId) {
        if (!bootstrap.isReady(requiredKeyId)) {
            throw new GamificationConfigUnavailableException(
                "Secret hash định danh gamification chưa sẵn sàng cho key " + requiredKeyId);
        }
        String salt;
        try {
            salt = Files.readString(Path.of(bootstrap.getVoteIpHashSaltFile()),
                StandardCharsets.UTF_8).trim();
        } catch (IOException exception) {
            throw new GamificationConfigUnavailableException(
                "Không thể đọc secret hash định danh gamification");
        }
        String canonical = salt + '|' + domain + '|' + (rawValue == null ? "" : rawValue.trim());
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(canonical.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("JVM không hỗ trợ SHA-256", exception);
        }
    }
}
