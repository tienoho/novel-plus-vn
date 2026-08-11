package com.java2nb.novel.service.gamification.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

@Component
public class GamificationBootstrapProperties implements GamificationSecretReadiness {

    @Value("${GAMIFICATION_CONFIG_SOURCE:ENV}")
    private String source = "ENV";

    @Value("${GAMIFICATION_CONFIG_REFRESH_MS:2000}")
    private long refreshMs = 2_000;

    @Value("${GAMIFICATION_CONFIG_MAX_STALE_MS:60000}")
    private long maxStaleMs = 60_000;

    @Value("${GAMIFICATION_FORCE_DISABLE:false}")
    private boolean forceDisable;

    @Value("${GAMIFICATION_VOTE_IP_HASH_KEY_ID:v1}")
    private String voteIpHashKeyId = "v1";

    @Value("${GAMIFICATION_VOTE_IP_HASH_SALT_FILE:/run/secrets/gamification_vote_ip_hash_salt}")
    private String voteIpHashSaltFile = "/run/secrets/gamification_vote_ip_hash_salt";

    public GamificationConfigSource resolveSource() {
        try {
            return GamificationConfigSource.valueOf(source.trim().toUpperCase());
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("GAMIFICATION_CONFIG_SOURCE không hợp lệ", exception);
        }
    }

    @Override
    public boolean isReady(String requiredKeyId) {
        if (requiredKeyId == null || !requiredKeyId.equals(voteIpHashKeyId)) {
            return false;
        }
        try {
            String secret = Files.readString(Path.of(voteIpHashSaltFile), StandardCharsets.UTF_8).trim();
            return secret.length() >= 32 && !"disabled".equalsIgnoreCase(secret);
        } catch (IOException | RuntimeException exception) {
            return false;
        }
    }

    public long getRefreshMs() {
        return refreshMs;
    }

    public long getMaxStaleMs() {
        return maxStaleMs;
    }

    public boolean isForceDisable() {
        return forceDisable;
    }

    public String getVoteIpHashKeyId() {
        return voteIpHashKeyId;
    }

    public String getVoteIpHashSaltFile() {
        return voteIpHashSaltFile;
    }
}
