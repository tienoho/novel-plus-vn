package com.java2nb.common.config;

import com.java2nb.system.dao.SysUserDao;
import com.java2nb.system.domain.UserDO;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrapRunner implements ApplicationRunner {

    private final SysUserDao userDao;
    private final PasswordEncoder passwordEncoder;
    private final Environment environment;

    @Value("${security.admin-bootstrap.username:admin}")
    private String username;

    @Value("${security.admin-bootstrap.password:}")
    private String bootstrapPassword;

    @Override
    public void run(ApplicationArguments args) {
        List<UserDO> users = userDao.list(Map.of("username", username));
        if (users.isEmpty()) {
            throw new IllegalStateException("Không tìm thấy tài khoản quản trị bootstrap: " + username);
        }
        UserDO user = users.get(0);
        if (isCostTwelveBcrypt(user.getPassword())) {
            return;
        }
        if (bootstrapPassword == null || bootstrapPassword.isBlank()) {
            if (environment.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("Thiếu ADMIN_BOOTSTRAP_PASSWORD cho lần khởi động production đầu tiên");
            }
            log.warn("Tài khoản quản trị chưa dùng BCrypt; đặt ADMIN_BOOTSTRAP_PASSWORD để kích hoạt đăng nhập");
            return;
        }
        validatePassword(bootstrapPassword);
        UserDO update = new UserDO();
        update.setUserId(user.getUserId());
        update.setPassword(passwordEncoder.encode(bootstrapPassword));
        update.setMustChangePassword(true);
        if (userDao.update(update) != 1) {
            throw new IllegalStateException("Không thể cập nhật mật khẩu bootstrap cho quản trị viên");
        }
        log.info("Đã khởi tạo mật khẩu BCrypt cho tài khoản quản trị {}", username);
    }

    private boolean isCostTwelveBcrypt(String value) {
        return value != null && value.matches("^\\$2[aby]\\$12\\$.{53}$");
    }

    private void validatePassword(String value) {
        if (value.length() < 12 || value.length() > 72
            || value.toLowerCase().contains("change-me") || "admin".equalsIgnoreCase(value)) {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_PASSWORD phải dài 12-72 ký tự và không phải mật khẩu mẫu");
        }
    }
}
