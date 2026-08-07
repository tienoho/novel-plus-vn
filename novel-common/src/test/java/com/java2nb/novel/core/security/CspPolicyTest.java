package com.java2nb.novel.core.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CspPolicyTest {

    @Test
    void createsIndependentUrlSafeNoncesAndStrictScriptPolicy() {
        String first = CspPolicy.newNonce();
        String second = CspPolicy.newNonce();

        assertThat(first).matches("[A-Za-z0-9_-]{24}").isNotEqualTo(second);
        assertThat(CspPolicy.value(first))
            .contains("script-src 'self' 'nonce-" + first + "'")
            .contains("script-src-attr 'none'")
            .doesNotContain("'unsafe-eval'", "script-src 'self' 'unsafe-inline'");
    }

    @Test
    void rejectsHeaderInjectionInNonce() {
        assertThatThrownBy(() -> CspPolicy.value("valid-nonce\r\nX-Evil: true"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
