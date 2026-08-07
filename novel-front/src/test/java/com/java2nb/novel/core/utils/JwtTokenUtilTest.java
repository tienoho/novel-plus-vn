package com.java2nb.novel.core.utils;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java2nb.novel.core.bean.UserDetails;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class JwtTokenUtilTest {

    private JwtTokenUtil tokens;

    @BeforeEach
    void setUp() throws Exception {
        tokens = new JwtTokenUtil(new ObjectMapper());
        set("secret", "jwt-test-secret-that-is-longer-than-thirty-two-characters");
        set("accessExpiration", 900L);
        set("refreshExpiration", 604800L);
    }

    @Test
    void separatesAccessAndRefreshTokensAndExposesServerSideRefreshMetadata() {
        UserDetails user = new UserDetails();
        user.setId(7L);
        user.setUsername("doc-gia");
        user.setNickName("Độc giả");

        JwtTokenUtil.TokenPair pair = tokens.generateTokens(user);

        assertThat(tokens.getUserDetailsFromToken(pair.accessToken()).getId()).isEqualTo(7L);
        assertThat(tokens.getUserDetailsFromToken(pair.refreshToken())).isNull();
        JwtTokenUtil.RefreshTokenDetails refresh = tokens.getRefreshTokenDetails(pair.refreshToken());
        assertThat(refresh).isNotNull();
        assertThat(refresh.userDetails().getId()).isEqualTo(7L);
        assertThat(refresh.jti()).isNotBlank();
        assertThat(refresh.expiresAt()).isAfter(java.time.Instant.now());
    }

    @Test
    void rejectsMalformedTokenWithoutReturningUserData() {
        assertThat(tokens.getUserDetailsFromToken("not-a-jwt")).isNull();
        assertThat(tokens.getRefreshTokenDetails("not-a-jwt")).isNull();
    }

    private void set(String name, Object value) throws Exception {
        Field field = JwtTokenUtil.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(tokens, value);
    }
}
