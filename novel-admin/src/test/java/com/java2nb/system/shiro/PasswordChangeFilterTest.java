package com.java2nb.system.shiro;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.java2nb.system.domain.UserDO;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import org.apache.shiro.subject.Subject;
import org.apache.shiro.util.ThreadContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class PasswordChangeFilterTest {

    private final TestablePasswordChangeFilter filter = new TestablePasswordChangeFilter();

    @AfterEach
    void clearSubject() {
        ThreadContext.remove();
    }

    @Test
    void redirectsNormalNavigationToThePersonalPasswordPage() throws Exception {
        bindUser(true);
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/index");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(filter.allowed(request, response)).isFalse();
        assertThat(filter.denied(request, response)).isFalse();
        assertThat(response.getRedirectedUrl()).isEqualTo("/sys/user/personal");
    }

    @Test
    void blocksStateChangingAjaxUntilPasswordHasBeenChanged() throws Exception {
        bindUser(true);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/sys/menu/save");
        request.addHeader("X-Requested-With", "XMLHttpRequest");
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(filter.allowed(request, response)).isFalse();
        assertThat(filter.denied(request, response)).isFalse();
        assertThat(response.getStatus()).isEqualTo(428);
    }

    @Test
    void permitsOnlyTheSelfServicePasswordFlowWhileFlagged() {
        bindUser(true);
        MockHttpServletResponse response = new MockHttpServletResponse();

        assertThat(filter.allowed(
            new MockHttpServletRequest("GET", "/sys/user/personal"), response)).isTrue();
        assertThat(filter.allowed(
            new MockHttpServletRequest("POST", "/sys/user/resetPwd"), response)).isTrue();
        assertThat(filter.allowed(
            new MockHttpServletRequest("POST", "/sys/user/updatePeronal"), response)).isFalse();
    }

    @Test
    void permitsTheFullAdminAreaAfterPasswordChange() {
        bindUser(false);

        assertThat(filter.allowed(
            new MockHttpServletRequest("POST", "/sys/menu/save"),
            new MockHttpServletResponse())).isTrue();
    }

    private void bindUser(boolean mustChangePassword) {
        UserDO user = new UserDO();
        user.setUserId(1L);
        user.setMustChangePassword(mustChangePassword);
        Subject subject = mock(Subject.class);
        when(subject.getPrincipal()).thenReturn(user);
        ThreadContext.bind(subject);
    }

    private static final class TestablePasswordChangeFilter extends PasswordChangeFilter {
        boolean allowed(ServletRequest request, ServletResponse response) {
            return isAccessAllowed(request, response, null);
        }

        boolean denied(ServletRequest request, ServletResponse response) throws Exception {
            return onAccessDenied(request, response);
        }
    }
}
