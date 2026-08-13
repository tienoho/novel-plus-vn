package com.java2nb.system.shiro;

import com.java2nb.common.redis.shiro.SerializeUtils;
import com.java2nb.system.domain.UserDO;
import org.apache.shiro.session.mgt.SimpleSession;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.support.DefaultSubjectContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "admin.e2e.session.fixture", matches = "true")
class AdminE2eSessionFixtureTest {

    @Test
    void writesAuthenticatedShiroSessionsForPlaywrightOnly() throws Exception {
        String configured = System.getProperty("admin.e2e.session.outputDir", "").trim();
        assertThat(configured).isNotBlank();
        Path output = Path.of(configured).toAbsolutePath().normalize();
        Files.createDirectories(output);

        String maker = writeSession(output, "maker", user(1L, "admin", "Quản trị viên E2E"));
        String checker = writeSession(output, "checker",
            user(990_102L, "e2e-checker", "Người duyệt E2E"));
        Files.writeString(output.resolve("sessions.properties"),
            "maker=" + maker + System.lineSeparator()
                + "checker=" + checker + System.lineSeparator(), StandardCharsets.UTF_8);
    }

    private String writeSession(Path output, String name, UserDO user) throws Exception {
        String id = UUID.randomUUID().toString();
        SimpleSession session = new SimpleSession("127.0.0.1");
        session.setId(id);
        session.setTimeout(30 * 60 * 1000L);
        session.setAttribute(DefaultSubjectContext.PRINCIPALS_SESSION_KEY,
            new SimplePrincipalCollection(user, UserRealm.class.getName()));
        session.setAttribute(DefaultSubjectContext.AUTHENTICATED_SESSION_KEY, Boolean.TRUE);
        byte[] serialized = SerializeUtils.serialize(session);
        assertThat(serialized).isNotEmpty();
        Files.write(output.resolve(name + ".session"), serialized);
        return id;
    }

    private UserDO user(long id, String username, String name) {
        UserDO user = new UserDO();
        user.setUserId(id);
        user.setUsername(username);
        user.setName(name);
        user.setDeptId(14L);
        user.setStatus(1);
        user.setMustChangePassword(false);
        return user;
    }
}
