package com.java2nb.novel.service;

import com.java2nb.novel.core.config.VnpayRecurringProperties;
import com.java2nb.novel.core.observability.NovelBusinessMetrics;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.java2nb.novel.service.finance.PiiCryptoService;
import com.java2nb.novel.service.subscription.ReadingSubscriptionProviderCharge;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalResult;
import com.java2nb.novel.service.subscription.ReadingSubscriptionRenewalService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VnpayRecurringRenewalProcessorTest {

    private ReadingSubscriptionRenewalService renewalService;
    private VnpayRecurringClient client;
    private PiiCryptoService cryptoService;
    private VnpayRecurringRenewalProcessor processor;
    private final Date now = Date.from(Instant.parse("2027-02-01T00:00:00Z"));

    @BeforeEach
    void setUp() {
        renewalService = mock(ReadingSubscriptionRenewalService.class);
        client = mock(VnpayRecurringClient.class);
        cryptoService = mock(PiiCryptoService.class);
        VnpayRecurringProperties properties = new VnpayRecurringProperties();
        properties.setEnabled(true);
        properties.setClientId("CLIENT01");
        properties.setUsername("merchantuser");
        properties.setPassword("a-secure-password");
        properties.setClientSecret("a-secure-client-secret");
        properties.setTmnCode("VNPAYREC");
        properties.setHashSecret("0123456789abcdef0123456789abcdef");
        properties.setReturnUrl("https://example.com/return");
        properties.setCancelUrl("https://example.com/cancel");
        when(cryptoService.isConfigured()).thenReturn(true);
        processor = new VnpayRecurringRenewalProcessor(renewalService, client, properties, cryptoService,
            new NovelBusinessMetrics(new SimpleMeterRegistry()));
    }

    @Test
    void settlesOnlyAfterProviderSettlement() {
        ReadingSubscriptionProviderCharge charge = charge("ciphertext", new Date(now.getTime() + 86_400_000));
        when(renewalService.getProviderCharge(81L)).thenReturn(charge);
        when(cryptoService.decrypt("ciphertext")).thenReturn("tokenABC123");
        when(client.charge(any())).thenReturn(VnpayRecurringChargeResult.settled("777821925535879168"));
        when(renewalService.settleProviderCharge(81L, 1, "777821925535879168", now))
            .thenReturn(ReadingSubscriptionRenewalResult.SETTLED);

        assertThat(processor.process(81L, now)).isEqualTo(ReadingSubscriptionRenewalResult.SETTLED);
        verify(renewalService).settleProviderCharge(81L, 1, "777821925535879168", now);
    }

    @Test
    void unknownProviderResultIsFailClosed() {
        when(renewalService.getProviderCharge(81L))
            .thenReturn(charge("ciphertext", new Date(now.getTime() + 86_400_000)));
        when(cryptoService.decrypt("ciphertext")).thenReturn("tokenABC123");
        when(client.charge(any())).thenReturn(VnpayRecurringChargeResult.pending("UNAVAILABLE"));
        when(renewalService.recordProviderPending(81L, 1, "UNAVAILABLE", now))
            .thenReturn(ReadingSubscriptionRenewalResult.PROVIDER_PENDING);

        assertThat(processor.process(81L, now))
            .isEqualTo(ReadingSubscriptionRenewalResult.PROVIDER_PENDING);
        verify(renewalService, never()).settleProviderCharge(eq(81L), eq(1), any(), eq(now));
    }

    @Test
    void expiredMandateFailsWithoutCallingProvider() {
        when(renewalService.getProviderCharge(81L))
            .thenReturn(charge("ciphertext", new Date(now.getTime() - 1)));
        when(renewalService.recordProviderFailure(81L, 1, "MANDATE_UNAVAILABLE", now))
            .thenReturn(ReadingSubscriptionRenewalResult.RETRY_SCHEDULED);

        assertThat(processor.process(81L, now))
            .isEqualTo(ReadingSubscriptionRenewalResult.RETRY_SCHEDULED);
        verify(client, never()).charge(any());
    }

    private ReadingSubscriptionProviderCharge charge(String ciphertext, Date expiry) {
        return new ReadingSubscriptionProviderCharge(81L, 51L, 11L, 1, 49_000L,
            now, "NPR81A1", "666821925535879168", ciphertext, expiry);
    }
}
