package com.java2nb.novel.service;

import com.java2nb.novel.core.config.PiiCryptoProperties;
import com.java2nb.novel.core.config.VnpayRecurringProperties;
import com.java2nb.novel.core.utils.ContentHashUtil;
import com.java2nb.novel.mapper.ReadingSubscriptionMandateMapper;
import com.java2nb.novel.mapper.ReadingSubscriptionMapper;
import com.java2nb.novel.service.finance.PiiCryptoService;
import com.java2nb.novel.service.subscription.ReadingSubscriptionMandateRow;
import com.java2nb.novel.service.subscription.ReadingSubscriptionPlanRow;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VnpayRecurringMandateServiceTest {

    private VnpayRecurringProperties properties;
    private VnpayRecurringSigner signer;
    private VnpayRecurringClient client;
    private ReadingSubscriptionMapper subscriptionMapper;
    private ReadingSubscriptionMandateMapper mandateMapper;
    private PiiCryptoService piiCryptoService;
    private VnpayRecurringMandateService service;

    @BeforeEach
    void setUp() {
        properties = configuredProperties();
        signer = new VnpayRecurringSigner(properties);
        client = mock(VnpayRecurringClient.class);
        subscriptionMapper = mock(ReadingSubscriptionMapper.class);
        mandateMapper = mock(ReadingSubscriptionMandateMapper.class);
        PiiCryptoProperties piiProperties = new PiiCryptoProperties();
        piiProperties.setEncryptionKey(Base64.getEncoder().encodeToString(
            "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.US_ASCII)));
        piiCryptoService = new PiiCryptoService(piiProperties);
        service = new VnpayRecurringMandateService(properties, signer, client,
            subscriptionMapper, mandateMapper, piiCryptoService);
    }

    @Test
    void createsPendingMandateBeforeCallingProvider() {
        ReadingSubscriptionPlanRow plan = new ReadingSubscriptionPlanRow();
        plan.setPlanVersion(3L);
        plan.setPriceVnd(100_000L);
        plan.setPeriodMonths(1);
        when(subscriptionMapper.selectActivePlanByCode("MONTHLY")).thenReturn(plan);
        when(client.initializeMandate(any())).thenAnswer(invocation -> {
            VnpayRecurringMandateCommand command = invocation.getArgument(0);
            verify(mandateMapper).insertPending(eq(7L), eq(command.merchantReference()),
                eq("mandate_0001"), any(), eq("MONTHLY"), eq(3L));
            return new VnpayRecurringMandateInitialization(command.merchantReference(),
                "666821925535879168", properties.getPayUrl(), properties.getTmnCode(), "data-key");
        });
        when(mandateMapper.registerProviderInitialization(any(), eq("666821925535879168"), any()))
            .thenReturn(1);

        VnpayRecurringMandateInitialization result = service.create(7L, "MONTHLY", 3L,
            "mandate_0001", "127.0.0.1", "Mozilla/5.0");

        assertThat(result.providerRecurringId()).isEqualTo("666821925535879168");
    }

    @Test
    void rejectsStalePlanVersionBeforeCreatingMandate() {
        ReadingSubscriptionPlanRow plan = new ReadingSubscriptionPlanRow();
        plan.setPlanVersion(4L);
        plan.setPriceVnd(100_000L);
        plan.setPeriodMonths(1);
        when(subscriptionMapper.selectActivePlanByCode("MONTHLY")).thenReturn(plan);

        assertThatThrownBy(() -> service.create(7L, "MONTHLY", 3L, "mandate_0001",
            "127.0.0.1", "Browser"))
            .isInstanceOf(IllegalArgumentException.class);
        verify(mandateMapper, never()).insertPending(anyLong(), any(), any(), any(), any(), anyLong());
    }

    @Test
    void marksPendingMandateFailedWhenProviderIsUnavailable() {
        stubMonthlyPlan();
        VnpayRecurringUnavailableException failure =
            new VnpayRecurringUnavailableException("Provider unavailable");
        when(client.initializeMandate(any())).thenThrow(failure);
        when(mandateMapper.selectByMerchantReference(any())).thenReturn(mandate("PENDING", 0L));
        when(mandateMapper.fail(9L, 0L)).thenReturn(1);

        assertThatThrownBy(() -> service.create(7L, "MONTHLY", 3L, "mandate_0001",
            "127.0.0.1", "Browser"))
            .isSameAs(failure);

        verify(mandateMapper).fail(9L, 0L);
    }

    @Test
    void marksPendingMandateFailedWhenProviderTransactionCannotBeRecorded() {
        stubMonthlyPlan();
        when(client.initializeMandate(any())).thenAnswer(invocation -> {
            VnpayRecurringMandateCommand command = invocation.getArgument(0);
            return new VnpayRecurringMandateInitialization(command.merchantReference(),
                "666821925535879168", properties.getPayUrl(), properties.getTmnCode(), "data-key");
        });
        when(mandateMapper.registerProviderInitialization(any(), eq("666821925535879168"), any()))
            .thenReturn(0);
        when(mandateMapper.selectByMerchantReference(any())).thenReturn(mandate("PENDING", 0L));
        when(mandateMapper.fail(9L, 0L)).thenReturn(1);

        assertThatThrownBy(() -> service.create(7L, "MONTHLY", 3L, "mandate_0001",
            "127.0.0.1", "Browser"))
            .isInstanceOf(VnpayRecurringUnavailableException.class)
            .hasMessage("Không thể ghi nhận giao dịch VNPAY Recurring");

        verify(mandateMapper).fail(9L, 0L);
    }

    @Test
    void preservesProviderFailureWhenCleanupAlsoFails() {
        stubMonthlyPlan();
        VnpayRecurringUnavailableException providerFailure =
            new VnpayRecurringUnavailableException("Provider unavailable");
        IllegalStateException cleanupFailure = new IllegalStateException("Database unavailable");
        when(client.initializeMandate(any())).thenThrow(providerFailure);
        when(mandateMapper.selectByMerchantReference(any())).thenThrow(cleanupFailure);

        assertThatThrownBy(() -> service.create(7L, "MONTHLY", 3L, "mandate_0001",
            "127.0.0.1", "Browser"))
            .isSameAs(providerFailure)
            .satisfies(error -> assertThat(error.getSuppressed()).containsExactly(cleanupFailure));
    }

    @Test
    void preservesProviderFailureWhenOptimisticCleanupLosesRace() {
        stubMonthlyPlan();
        VnpayRecurringRejectedException providerFailure = new VnpayRecurringRejectedException("99");
        when(client.initializeMandate(any())).thenThrow(providerFailure);
        when(mandateMapper.selectByMerchantReference(any())).thenReturn(mandate("PENDING", 0L));
        when(mandateMapper.fail(9L, 0L)).thenReturn(0);

        assertThatThrownBy(() -> service.create(7L, "MONTHLY", 3L, "mandate_0001",
            "127.0.0.1", "Browser"))
            .isSameAs(providerFailure);

        verify(mandateMapper).fail(9L, 0L);
    }

    @Test
    void replaysEncryptedProviderInitializationAfterResponseLoss() {
        stubMonthlyPlan();
        ReadingSubscriptionMandateRow pending = mandate("PENDING", 1L);
        pending.setClientRequestId("mandate_0001");
        pending.setPlanCodeSnapshot("MONTHLY");
        pending.setAcceptedPlanVersion(3L);
        pending.setRequestHash(ContentHashUtil.sha256Hex(
            "VNPAY_RECURRING_MANDATE|7|mandate_0001|MONTHLY|3"));
        pending.setProviderDataKeyCiphertext(piiCryptoService.encrypt("data-key"));
        when(mandateMapper.selectByClientRequestId(7L, "mandate_0001")).thenReturn(pending);

        VnpayRecurringMandateInitialization result = service.create(7L, "MONTHLY", 3L,
            "mandate_0001", "127.0.0.1", "Browser");

        assertThat(result.providerRecurringId()).isEqualTo("666821925535879168");
        assertThat(result.dataKey()).isEqualTo("data-key");
        verify(client, never()).initializeMandate(any());
        verify(mandateMapper, never()).insertPending(anyLong(), any(), any(), any(), any(), anyLong());
    }

    @Test
    void rejectsReusedClientRequestIdWithDifferentPayload() {
        stubMonthlyPlan();
        ReadingSubscriptionMandateRow pending = mandate("PENDING", 1L);
        pending.setClientRequestId("mandate_0001");
        pending.setRequestHash("0".repeat(64));
        when(mandateMapper.selectByClientRequestId(7L, "mandate_0001")).thenReturn(pending);

        assertThatThrownBy(() -> service.create(7L, "MONTHLY", 3L, "mandate_0001",
            "127.0.0.1", "Browser"))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Mã yêu cầu");

        verify(client, never()).initializeMandate(any());
    }

    @Test
    void activatesEncryptedTokenAndTreatsReplayAsIdempotent() {
        ReadingSubscriptionMandateRow pending = mandate("PENDING", 0L);
        when(mandateMapper.selectByMerchantReferenceForUpdate("NP123")).thenReturn(pending);
        when(mandateMapper.activate(eq(9L), eq(0L), any(), any(), any())).thenReturn(1);
        Map<String, String> callback = signedCallback();

        assertThat(service.processIpn(callback))
            .isEqualTo(VnpayRecurringMandateService.CallbackResult.SUCCESS);
        ArgumentCaptor<String> ciphertext = ArgumentCaptor.forClass(String.class);
        verify(mandateMapper).activate(eq(9L), eq(0L), ciphertext.capture(), any(), any());
        assertThat(piiCryptoService.decrypt(ciphertext.getValue())).isEqualTo("tokenABC123");

        when(mandateMapper.selectByMerchantReferenceForUpdate("NP123")).thenReturn(mandate("ACTIVE", 1L));
        assertThat(service.processIpn(callback))
            .isEqualTo(VnpayRecurringMandateService.CallbackResult.ALREADY_PROCESSED);
    }

    @Test
    void rejectsTamperedCallbackBeforeDatabaseLookup() {
        Map<String, String> callback = signedCallback();
        callback.put("vnp_app_user_id", "999");

        assertThat(service.processIpn(callback))
            .isEqualTo(VnpayRecurringMandateService.CallbackResult.INVALID_CHECKSUM);
        verify(mandateMapper, never()).selectByMerchantReferenceForUpdate(any());
    }

    private Map<String, String> signedCallback() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("vnp_tmn_code", "VNPAYREC");
        values.put("vnp_app_user_id", "123");
        values.put("vnp_token", "tokenABC123");
        values.put("vnp_token_exp_date", "20271231");
        values.put("vnp_command", "recurring");
        values.put("vnp_txn_ref", "NP123");
        values.put("vnp_response_code", "00");
        values.put("vnp_transaction_status", "00");
        values.put("vnp_pay_date", "20260801123000");
        values.put("vnp_secure_hash",
            "3e81597d478ff02b1f35c934abc97044c89cafea90e5356ab589b0fa4f91e0742"
                + "48c7d292e3eb3443d53afaac19993bbd3906f1b50f4159106e213262a94815b");
        return values;
    }

    private ReadingSubscriptionMandateRow mandate(String status, long version) {
        ReadingSubscriptionMandateRow row = new ReadingSubscriptionMandateRow();
        row.setId(9L);
        row.setUserId(123L);
        row.setMerchantReference("NP123");
        row.setProviderRecurringId("666821925535879168");
        row.setStatus(status);
        row.setVersion(version);
        return row;
    }

    private void stubMonthlyPlan() {
        ReadingSubscriptionPlanRow plan = new ReadingSubscriptionPlanRow();
        plan.setPlanVersion(3L);
        plan.setPriceVnd(100_000L);
        plan.setPeriodMonths(1);
        when(subscriptionMapper.selectActivePlanByCode("MONTHLY")).thenReturn(plan);
    }

    private VnpayRecurringProperties configuredProperties() {
        VnpayRecurringProperties value = new VnpayRecurringProperties();
        value.setEnabled(true);
        value.setClientId("CLIENT01");
        value.setUsername("merchantuser");
        value.setPassword("a-secure-password");
        value.setClientSecret("a-secure-client-secret");
        value.setTmnCode("VNPAYREC");
        value.setHashSecret("0123456789abcdef0123456789abcdef");
        value.setReturnUrl("https://khoithu.vn/return");
        value.setCancelUrl("https://khoithu.vn/cancel");
        return value;
    }
}
