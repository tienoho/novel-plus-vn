package com.java2nb.novel.controller;

import com.java2nb.novel.common.annotation.LimitType;
import com.java2nb.novel.common.annotation.RateLimit;
import com.java2nb.novel.common.service.TotpService;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.cache.CacheService;
import com.java2nb.novel.core.utils.AuthCookieService;
import com.java2nb.novel.core.utils.IpUtil;
import com.java2nb.novel.core.utils.JwtTokenUtil;
import com.java2nb.novel.core.utils.RandomValidateCodeUtil;
import com.java2nb.novel.core.utils.RefreshTokenSessionService;
import com.java2nb.novel.entity.User;
import com.java2nb.novel.service.BookService;
import com.java2nb.novel.service.UserService;
import com.java2nb.novel.service.wallet.WalletLedgerService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.bind.annotation.PostMapping;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UserControllerMappingTest {

    @Test
    void registrationEndpointRemainsMapped() throws Exception {
        Method method = UserController.class.getDeclaredMethod(
            "register", User.class, String.class, HttpServletRequest.class, HttpServletResponse.class);
        PostMapping mapping = method.getAnnotation(PostMapping.class);

        assertThat(mapping).isNotNull();
        assertThat(mapping.value()).containsExactly("register");
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);
        assertThat(rateLimit).isNotNull();
        assertThat(rateLimit.key()).isEqualTo("register");
        assertThat(rateLimit.count()).isEqualTo(5);
        assertThat(rateLimit.limitType()).isEqualTo(LimitType.IP);
    }

    @Test
    void registrationConsumesCaptchaAtomicallyBeforeCreatingSession() {
        Dependencies dependencies = dependencies();
        User user = new User();
        UserDetails details = mock(UserDetails.class);
        JwtTokenUtil.TokenPair tokens = new JwtTokenUtil.TokenPair("access", "refresh");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/register");
        MockHttpServletResponse response = new MockHttpServletResponse();
        String key = RandomValidateCodeUtil.RANDOM_CODE_KEY + ":" + IpUtil.getRealIp(request);
        when(dependencies.cache.compareAndDelete(key, "ABCD")).thenReturn(true);
        when(dependencies.users.register(user)).thenReturn(details);
        when(dependencies.sessions.issue(details)).thenReturn(tokens);

        dependencies.controller.register(user, "ABCD", request, response);

        verify(dependencies.cache).compareAndDelete(key, "ABCD");
        verify(dependencies.users).register(user);
        verify(dependencies.cookies).write(response, tokens);
    }

    @Test
    void registrationStopsBeforeUserCreationWhenCaptchaCannotBeConsumed() {
        Dependencies dependencies = dependencies();
        User user = new User();
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/user/register");
        String key = RandomValidateCodeUtil.RANDOM_CODE_KEY + ":" + IpUtil.getRealIp(request);
        when(dependencies.cache.compareAndDelete(key, "WRONG")).thenReturn(false);

        dependencies.controller.register(user, "WRONG", request, new MockHttpServletResponse());

        verify(dependencies.cache).compareAndDelete(key, "WRONG");
        verify(dependencies.users, never()).register(user);
    }

    private Dependencies dependencies() {
        CacheService cache = mock(CacheService.class);
        UserService users = mock(UserService.class);
        BookService books = mock(BookService.class);
        WalletLedgerService wallet = mock(WalletLedgerService.class);
        TotpService totp = mock(TotpService.class);
        AuthCookieService cookies = mock(AuthCookieService.class);
        RefreshTokenSessionService sessions = mock(RefreshTokenSessionService.class);
        return new Dependencies(cache, users, cookies, sessions,
            new UserController(cache, users, books, wallet, totp, cookies, sessions));
    }

    private record Dependencies(CacheService cache, UserService users, AuthCookieService cookies,
                                RefreshTokenSessionService sessions, UserController controller) {
    }
}
