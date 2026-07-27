package com.java2nb.novel.core.utils;

import com.java2nb.novel.entity.User;
import io.github.xxyopen.web.valid.AddGroup;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VietnamesePhoneValidationTest {
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void acceptsVietnameseMobilePrefixes() {
        for (String phone : new String[] {
            "0321234567", "0521234567", "0701234567", "0811234567", "0912345678"
        }) {
            assertThat(usernameViolations(phone)).as(phone).isZero();
        }
    }

    @Test
    void rejectsLegacyChineseAndInvalidVietnameseNumbers() {
        for (String phone : new String[] {
            "13500000001", "0212345678", "091234567", "+84912345678"
        }) {
            assertThat(usernameViolations(phone)).as(phone).isEqualTo(1);
        }
    }

    private long usernameViolations(String phone) {
        User user = new User();
        user.setUsername(phone);
        user.setPassword("123456");
        return validator.validate(user, AddGroup.class).stream()
            .filter(violation -> "username".equals(violation.getPropertyPath().toString()))
            .count();
    }
}
