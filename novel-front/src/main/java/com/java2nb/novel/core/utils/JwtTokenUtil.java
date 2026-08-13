package com.java2nb.novel.core.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java2nb.novel.core.bean.UserDetails;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenUtil {

    private static final String CLAIM_TOKEN_TYPE = "token_type";
    private static final String ACCESS = "access";
    private static final String REFRESH = "refresh";

    private final ObjectMapper objectMapper;

    @Value("${jwt.secret}")
    private String secret;

    @Value("${jwt.access-expiration:900}")
    private long accessExpiration;

    @Value("${jwt.refresh-expiration:604800}")
    private long refreshExpiration;

    @SneakyThrows
    TokenPair generateTokens(UserDetails userDetails) {
        String subject = objectMapper.writeValueAsString(userDetails);
        return new TokenPair(
            generateToken(subject, ACCESS, accessExpiration),
            generateToken(subject, REFRESH, refreshExpiration));
    }

    public UserDetails getUserDetailsFromToken(String accessToken) {
        return readUser(accessToken, ACCESS);
    }

    RefreshTokenDetails getRefreshTokenDetails(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser().setSigningKey(secret).parseClaimsJws(refreshToken).getBody();
            if (!REFRESH.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))
                || claims.getId() == null || claims.getExpiration() == null) {
                return null;
            }
            UserDetails user = objectMapper.readValue(claims.getSubject(), UserDetails.class);
            if (user.getId() == null) {
                return null;
            }
            return new RefreshTokenDetails(user, claims.getId(), claims.getExpiration().toInstant());
        } catch (Exception exception) {
            log.debug("Refresh JWT không hợp lệ: {}", exception.getClass().getSimpleName());
            return null;
        }
    }

    private String generateToken(String subject, String type, long lifetimeSeconds) {
        Date now = new Date();
        return Jwts.builder()
            .setId(UUID.randomUUID().toString())
            .setSubject(subject)
            .setIssuedAt(now)
            .setExpiration(new Date(now.getTime() + lifetimeSeconds * 1000))
            .addClaims(Map.of(CLAIM_TOKEN_TYPE, type))
            .signWith(SignatureAlgorithm.HS512, secret)
            .compact();
    }

    private UserDetails readUser(String token, String expectedType) {
        if (token == null || token.isBlank()) {
            return null;
        }
        try {
            Claims claims = Jwts.parser().setSigningKey(secret).parseClaimsJws(token).getBody();
            if (!expectedType.equals(claims.get(CLAIM_TOKEN_TYPE, String.class))) {
                return null;
            }
            return objectMapper.readValue(claims.getSubject(), UserDetails.class);
        } catch (Exception exception) {
            log.debug("JWT không hợp lệ: {}", exception.getClass().getSimpleName());
            return null;
        }
    }

    public record TokenPair(String accessToken, String refreshToken) {
    }

    record RefreshTokenDetails(UserDetails userDetails, String jti, Instant expiresAt) {
    }
}
