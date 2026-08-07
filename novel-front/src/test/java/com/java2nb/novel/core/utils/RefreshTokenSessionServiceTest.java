package com.java2nb.novel.core.utils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.cache.CacheService;
import java.lang.reflect.Field;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RefreshTokenSessionServiceTest {

    private CacheService cacheService;
    private JwtTokenUtil jwtTokenUtil;
    private RefreshTokenSessionService sessions;
    private UserDetails user;

    @BeforeEach
    void setUp() throws Exception {
        cacheService = mock(CacheService.class);
        jwtTokenUtil = new JwtTokenUtil(new ObjectMapper());
        set(jwtTokenUtil, "secret", "jwt-test-secret-that-is-longer-than-thirty-two-characters");
        set(jwtTokenUtil, "accessExpiration", 900L);
        set(jwtTokenUtil, "refreshExpiration", 604800L);
        sessions = new RefreshTokenSessionService(jwtTokenUtil, cacheService);

        user = new UserDetails();
        user.setId(17L);
        user.setUsername("doc-gia");
        user.setNickName("Độc giả");
    }

    @Test
    void storesJtiAndAllowsRefreshTokenToBeConsumedOnlyOnce() {
        JwtTokenUtil.TokenPair original = sessions.issue(user);
        JwtTokenUtil.RefreshTokenDetails details =
            jwtTokenUtil.getRefreshTokenDetails(original.refreshToken());
        String key = "auth:refresh:" + details.jti();

        verify(cacheService).set(eq(key), eq("17"), anyLong());
        when(cacheService.compareAndDelete(key, "17")).thenReturn(true, false);

        assertThat(sessions.rotate(original.refreshToken())).isNotNull();
        assertThat(sessions.rotate(original.refreshToken())).isNull();
    }

    @Test
    void logoutRevokesTheRefreshTokenBeforeCookiesAreCleared() {
        JwtTokenUtil.TokenPair original = sessions.issue(user);
        JwtTokenUtil.RefreshTokenDetails details =
            jwtTokenUtil.getRefreshTokenDetails(original.refreshToken());
        String key = "auth:refresh:" + details.jti();
        when(cacheService.compareAndDelete(key, "17")).thenReturn(true, false);

        sessions.revoke(original.refreshToken());

        assertThat(sessions.rotate(original.refreshToken())).isNull();
        verify(cacheService, times(2)).compareAndDelete(key, "17");
    }

    @Test
    void rejectsReplacementWhenRefreshSessionBelongsToAnotherUser() {
        JwtTokenUtil.TokenPair original = sessions.issue(user);
        JwtTokenUtil.RefreshTokenDetails details =
            jwtTokenUtil.getRefreshTokenDetails(original.refreshToken());
        when(cacheService.compareAndDelete("auth:refresh:" + details.jti(), "17"))
            .thenReturn(true);
        UserDetails anotherUser = new UserDetails();
        anotherUser.setId(18L);

        assertThat(sessions.replace(original.refreshToken(), anotherUser)).isNull();
        verify(cacheService, never()).compareAndDelete("auth:refresh:" + details.jti(), "17");
    }

    private void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
