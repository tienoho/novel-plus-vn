package com.java2nb.novel.core.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthCookieServiceTest {

    @Test
    void writesHttpOnlySecureAuthCookiesWithoutExposingTokensToScript() throws Exception {
        AuthCookieService service = new AuthCookieService();
        set(service, "secure", true);
        set(service, "accessExpiration", 900L);
        set(service, "refreshExpiration", 604800L);
        MockHttpServletResponse response = new MockHttpServletResponse();

        service.write(response, new JwtTokenUtil.TokenPair("access-secret", "refresh-secret"));

        List<String> cookies = response.getHeaders(HttpHeaders.SET_COOKIE);
        assertThat(cookies).anySatisfy(value -> assertThat(value)
            .contains("NovelAccess=access-secret", "HttpOnly", "Secure", "SameSite=Lax"));
        assertThat(cookies).anySatisfy(value -> assertThat(value)
            .contains("NovelRefresh=refresh-secret", "HttpOnly", "Secure", "SameSite=Strict"));
        assertThat(cookies).anySatisfy(value -> assertThat(value)
            .contains("XSRF-TOKEN=").doesNotContain("HttpOnly"));
    }

    private void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
