package com.java2nb.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

class PasswordSecurityConfigTest {

    @Test
    void bcryptUsesSaltAndMatchesOnlyTheOriginalPassword() {
        PasswordEncoder encoder = new PasswordSecurityConfig().passwordEncoder();

        String first = encoder.encode("Mat-khau-quan-tri-2026");
        String second = encoder.encode("Mat-khau-quan-tri-2026");

        assertThat(first).startsWith("$2").isNotEqualTo(second);
        assertThat(encoder.matches("Mat-khau-quan-tri-2026", first)).isTrue();
        assertThat(encoder.matches("sai-mat-khau", first)).isFalse();
    }
}
