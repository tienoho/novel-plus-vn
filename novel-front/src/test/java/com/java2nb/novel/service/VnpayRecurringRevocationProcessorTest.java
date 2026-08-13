package com.java2nb.novel.service;

import com.java2nb.novel.core.config.VnpayRecurringProperties;
import com.java2nb.novel.mapper.ReadingSubscriptionMandateMapper;
import com.java2nb.novel.service.finance.PiiCryptoService;
import com.java2nb.novel.service.subscription.ReadingSubscriptionMandateRow;
import java.time.Instant;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VnpayRecurringRevocationProcessorTest {
    private VnpayRecurringProperties properties;
    private ReadingSubscriptionMandateMapper mapper;
    private PiiCryptoService crypto;
    private VnpayRecurringClient client;
    private VnpayRecurringRevocationProcessor processor;
    private Date now;

    @BeforeEach
    void setUp() {
        properties = new VnpayRecurringProperties();
        properties.setEnabled(true);
        properties.setClientId("CLIENT01");
        properties.setUsername("merchantuser");
        properties.setPassword("a-secure-password");
        properties.setClientSecret("a-secure-client-secret");
        properties.setTmnCode("VNPAYREC");
        properties.setHashSecret("0123456789abcdef0123456789abcdef");
        properties.setReturnUrl("https://example.com/return");
        properties.setCancelUrl("https://example.com/cancel");
        mapper = mock(ReadingSubscriptionMandateMapper.class);
        crypto = mock(PiiCryptoService.class);
        client = mock(VnpayRecurringClient.class);
        processor = new VnpayRecurringRevocationProcessor(properties, mapper, crypto, client);
        now = Date.from(Instant.parse("2027-02-01T00:00:00Z"));
        when(crypto.isConfigured()).thenReturn(true);
    }

    @Test
    void revokesAndErasesProviderTokenAfterDesiredProviderState() {
        ReadingSubscriptionMandateRow row = mandate();
        when(mapper.claimRevocation(eq(71L), eq(now), any())).thenReturn(1);
        when(mapper.selectById(71L)).thenReturn(row);
        when(crypto.decrypt("ciphertext")).thenReturn("tokenABC123");
        when(client.cancel(any())).thenReturn(VnpayRecurringCancelResult.revoked("00"));
        when(mapper.markRevoked(71L, 4L, now)).thenReturn(1);

        assertThat(processor.process(71L, now))
            .isEqualTo(VnpayRecurringRevocationResult.REVOKED);

        verify(mapper).markRevoked(71L, 4L, now);
    }

    @Test
    void providerFailureReleasesLeaseAndSchedulesRetry() {
        ReadingSubscriptionMandateRow row = mandate();
        when(mapper.claimRevocation(eq(71L), eq(now), any())).thenReturn(1);
        when(mapper.selectById(71L)).thenReturn(row);
        when(crypto.decrypt("ciphertext")).thenReturn("tokenABC123");
        when(client.cancel(any())).thenReturn(VnpayRecurringCancelResult.retry("99"));
        when(mapper.scheduleRevocationRetry(eq(71L), eq(4L), any(), eq("99"))).thenReturn(1);

        assertThat(processor.process(71L, now))
            .isEqualTo(VnpayRecurringRevocationResult.RETRY_SCHEDULED);
        verify(mapper).scheduleRevocationRetry(eq(71L), eq(4L), any(), eq("99"));
    }

    @Test
    void secondWorkerCannotCallProviderWithoutLease() {
        when(mapper.claimRevocation(eq(71L), eq(now), any())).thenReturn(0);

        assertThat(processor.process(71L, now))
            .isEqualTo(VnpayRecurringRevocationResult.NOT_DUE);
        verify(client, never()).cancel(any());
        verify(mapper, never()).markRevoked(anyLong(), anyLong(), any());
    }

    private ReadingSubscriptionMandateRow mandate() {
        ReadingSubscriptionMandateRow row = new ReadingSubscriptionMandateRow();
        row.setId(71L);
        row.setUserId(11L);
        row.setProviderRecurringId("666821925535879168");
        row.setProviderTokenCiphertext("ciphertext");
        row.setStatus("REVOKE_PENDING");
        row.setVersion(4L);
        return row;
    }
}
