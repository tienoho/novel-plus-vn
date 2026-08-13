package com.java2nb.novel.core.utils;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.lang.reflect.Field;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceCookieServiceTest {

    @Test
    void reusesValidAnonymousDeviceCookieWithoutWritingAnotherCookie() throws Exception {
        DeviceCookieService service = new DeviceCookieService();
        setSecure(service, true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String deviceId = UUID.randomUUID().toString();
        request.setCookies(new Cookie(DeviceCookieService.COOKIE_NAME, deviceId));

        assertThat(service.resolve(request, response)).isEqualTo(deviceId);
        assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).isEmpty();
    }

    @Test
    void replacesInvalidValueWithHttpOnlySecureSameSiteCookie() throws Exception {
        DeviceCookieService service = new DeviceCookieService();
        setSecure(service, true);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setCookies(new Cookie(DeviceCookieService.COOKIE_NAME, "not-a-uuid"));

        String deviceId = service.resolve(request, response);

        assertThat(UUID.fromString(deviceId).toString()).isEqualTo(deviceId);
        assertThat(response.getHeader(HttpHeaders.SET_COOKIE))
            .contains(DeviceCookieService.COOKIE_NAME + "=" + deviceId)
            .contains("Path=/", "Max-Age=31536000", "Secure", "HttpOnly", "SameSite=Lax");
    }

    private void setSecure(DeviceCookieService service, boolean secure) throws Exception {
        Field field = DeviceCookieService.class.getDeclaredField("secure");
        field.setAccessible(true);
        field.set(service, secure);
    }
}
