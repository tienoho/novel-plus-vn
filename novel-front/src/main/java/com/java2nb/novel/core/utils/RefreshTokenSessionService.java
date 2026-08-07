package com.java2nb.novel.core.utils;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.cache.CacheService;
import java.time.Duration;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * Quản lý vòng đời refresh token bằng trạng thái phiên lưu trong Redis.
 */
@Service
@RequiredArgsConstructor
public class RefreshTokenSessionService {

    private static final String KEY_PREFIX = "auth:refresh:";

    private final JwtTokenUtil jwtTokenUtil;
    private final CacheService cacheService;

    public JwtTokenUtil.TokenPair issue(UserDetails userDetails) {
        JwtTokenUtil.TokenPair tokens = jwtTokenUtil.generateTokens(userDetails);
        JwtTokenUtil.RefreshTokenDetails refresh =
            jwtTokenUtil.getRefreshTokenDetails(tokens.refreshToken());
        if (refresh == null) {
            throw new IllegalStateException("Không thể tạo metadata refresh token");
        }
        long ttlSeconds = Math.max(1L,
            Duration.between(Instant.now(), refresh.expiresAt()).getSeconds());
        cacheService.set(key(refresh.jti()), String.valueOf(refresh.userDetails().getId()), ttlSeconds);
        return tokens;
    }

    public JwtTokenUtil.TokenPair rotate(String refreshToken) {
        JwtTokenUtil.RefreshTokenDetails current =
            jwtTokenUtil.getRefreshTokenDetails(refreshToken);
        return current == null || !consume(current) ? null : issue(current.userDetails());
    }

    public JwtTokenUtil.TokenPair replace(String refreshToken, UserDetails currentUserDetails) {
        JwtTokenUtil.RefreshTokenDetails current =
            jwtTokenUtil.getRefreshTokenDetails(refreshToken);
        if (current == null || !current.userDetails().getId().equals(currentUserDetails.getId())
            || !consume(current)) {
            return null;
        }
        return issue(currentUserDetails);
    }

    public void revoke(String refreshToken) {
        JwtTokenUtil.RefreshTokenDetails details =
            jwtTokenUtil.getRefreshTokenDetails(refreshToken);
        if (details != null) {
            consume(details);
        }
    }

    private boolean consume(JwtTokenUtil.RefreshTokenDetails details) {
        String userId = String.valueOf(details.userDetails().getId());
        return cacheService.compareAndDelete(key(details.jti()), userId);
    }

    private String key(String jti) {
        return KEY_PREFIX + jti;
    }
}
