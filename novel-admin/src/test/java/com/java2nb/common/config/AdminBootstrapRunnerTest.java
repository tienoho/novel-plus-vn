package com.java2nb.common.config;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.java2nb.system.dao.SysUserDao;
import com.java2nb.system.domain.UserDO;
import java.lang.reflect.Field;
import java.util.List;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.ApplicationArguments;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

class AdminBootstrapRunnerTest {

    @Test
    void leavesExistingCostTwelveBcryptUntouched() throws Exception {
        SysUserDao dao = mock(SysUserDao.class);
        UserDO user = user(new BCryptPasswordEncoder(12).encode("Mat-khau-quan-tri-2026"));
        when(dao.list(any())).thenReturn(List.of(user));
        AdminBootstrapRunner runner = runner(dao, new MockEnvironment(), "");

        runner.run(mock(ApplicationArguments.class));

        verify(dao, never()).update(any());
    }

    @Test
    void productionRejectsMissingBootstrapSecretForLegacyHash() throws Exception {
        SysUserDao dao = mock(SysUserDao.class);
        when(dao.list(any())).thenReturn(List.of(user("!RESET_REQUIRED!")));
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("prod");
        AdminBootstrapRunner runner = runner(dao, environment, "");

        assertThatThrownBy(() -> runner.run(mock(ApplicationArguments.class)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("ADMIN_BOOTSTRAP_PASSWORD");
    }

    @Test
    void bootstrapPasswordRequiresAChangeOnFirstLogin() throws Exception {
        SysUserDao dao = mock(SysUserDao.class);
        when(dao.list(any())).thenReturn(List.of(user("!RESET_REQUIRED!")));
        when(dao.update(any())).thenReturn(1);
        AdminBootstrapRunner runner = runner(
            dao, new MockEnvironment(), "Mat-khau-bootstrap-2026");

        runner.run(mock(ApplicationArguments.class));

        ArgumentCaptor<UserDO> update = ArgumentCaptor.forClass(UserDO.class);
        verify(dao).update(update.capture());
        assertThat(update.getValue().getMustChangePassword()).isTrue();
        assertThat(new BCryptPasswordEncoder(12).matches(
            "Mat-khau-bootstrap-2026", update.getValue().getPassword())).isTrue();
    }

    private AdminBootstrapRunner runner(SysUserDao dao, MockEnvironment environment, String password)
        throws Exception {
        AdminBootstrapRunner runner = new AdminBootstrapRunner(
            dao, new BCryptPasswordEncoder(12), environment);
        set(runner, "username", "admin");
        set(runner, "bootstrapPassword", password);
        return runner;
    }

    private UserDO user(String password) {
        UserDO user = new UserDO();
        user.setUserId(1L);
        user.setUsername("admin");
        user.setPassword(password);
        return user;
    }

    private void set(Object target, String name, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }
}
