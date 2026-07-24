package com.java2nb.novel.service.finance;

import com.java2nb.novel.core.config.PiiCryptoProperties;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PiiCryptoServiceTest {

    @Test
    void encryptsWithRandomAesGcmAndCreatesStableHmac() {
        PiiCryptoProperties properties = new PiiCryptoProperties();
        properties.setEncryptionKey(Base64.getEncoder().encodeToString(new byte[32]));
        PiiCryptoService service = new PiiCryptoService(properties);

        String first = service.encrypt("001234567890");
        String second = service.encrypt("001234567890");

        assertThat(first).startsWith("v1:").doesNotContain("001234567890").isNotEqualTo(second);
        assertThat(service.decrypt(first)).isEqualTo("001234567890");
        assertThat(service.deterministicHash("IDENTITY:001234567890"))
            .hasSize(64)
            .isEqualTo(service.deterministicHash("IDENTITY:001234567890"))
            .isNotEqualTo(service.deterministicHash("IDENTITY:001234567891"));
    }

    @Test
    void refusesPiiWhenKeyIsMissingOrInvalid() {
        PiiCryptoProperties properties = new PiiCryptoProperties();
        properties.setEncryptionKey("disabled");
        PiiCryptoService service = new PiiCryptoService(properties);

        assertThat(service.isConfigured()).isFalse();
        assertThatThrownBy(() -> service.encrypt("sensitive"))
            .isInstanceOf(PiiEncryptionUnavailableException.class);
    }
}
