package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.config.ReaderEntitlementProperties;
import com.java2nb.novel.core.config.VietQrProperties;
import com.java2nb.novel.core.config.VnpayProperties;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.core.payment.PaymentAdapterFactory;
import com.java2nb.novel.core.payment.PaymentAdapter;
import com.java2nb.novel.core.payment.PaymentCreationResult;
import com.java2nb.novel.service.OrderService;
import com.java2nb.novel.service.ReadingSubscriptionCheckoutCreation;
import com.java2nb.novel.service.VnpayRecurringMandateService;
import com.java2nb.novel.service.VnpayRecurringMandateInitialization;
import com.java2nb.novel.service.VnpayRecurringMandateState;
import com.java2nb.novel.dto.subscription.ReadingSubscriptionCheckoutRequest;
import com.java2nb.novel.dto.subscription.ReadingSubscriptionCancelRequest;
import com.java2nb.novel.dto.subscription.VnpayRecurringMandateRequest;
import com.java2nb.novel.service.subscription.ReadingSubscriptionService;
import com.java2nb.novel.service.subscription.ReadingSubscriptionCheckoutOptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.anyString;

class ReadingSubscriptionControllerTest {
    private ReadingSubscriptionService service;
    private ReaderEntitlementProperties properties;
    private VnpayRecurringMandateService mandateService;
    private ReadingSubscriptionController controller;

    @BeforeEach
    void setUp() {
        service = mock(ReadingSubscriptionService.class);
        properties = new ReaderEntitlementProperties();
        properties.setEnabled(true);
        UserDetails user = mock(UserDetails.class);
        when(user.getId()).thenReturn(101L);
        mandateService = mock(VnpayRecurringMandateService.class);
        controller = new ReadingSubscriptionController(service, properties,
            mock(OrderService.class), new PaymentAdapterFactory(List.of()),
            new VnpayProperties(), new VietQrProperties(), mandateService) {
            @Override
            protected UserDetails getUserDetails(jakarta.servlet.http.HttpServletRequest request) {
                return user;
            }
        };
    }

    @Test
    void readsOnlyCurrentUsersSubscriptionAndPeriodHistory() {
        MockHttpServletRequest request = new MockHttpServletRequest();

        controller.getCurrent(request);
        controller.getPeriodGrants(51L, 25, request);

        verify(service).getCurrentSubscription(101L);
        verify(service).listPeriodGrants(101L, 51L, 25);
    }

    @Test
    void disabledFeatureStopsBeforeReadingSubscriptionData() {
        properties.setEnabled(false);

        assertThatThrownBy(() -> controller.listPlans(new MockHttpServletRequest()))
            .isInstanceOf(BusinessException.class);

        verify(service, never()).listActivePlans();
    }

    @Test
    void checkoutUsesAuthenticatedUserAndConfiguredServerAdapter() {
        OrderService orderService = mock(OrderService.class);
        PaymentAdapter adapter = mock(PaymentAdapter.class);
        when(adapter.getChannelCode()).thenReturn((byte) 4);
        when(adapter.getChannelName()).thenReturn("VNPAY");
        when(adapter.createDepositOrder(any())).thenReturn(PaymentCreationResult.builder()
            .success(true).paymentUrl("https://pay.example/checkout").build());
        VnpayProperties vnpay = configuredVnpay();
        UserDetails user = mock(UserDetails.class);
        when(user.getId()).thenReturn(101L);
        controller = new ReadingSubscriptionController(service, properties, orderService,
            new PaymentAdapterFactory(List.of(adapter)), vnpay, new VietQrProperties(),
            mock(VnpayRecurringMandateService.class)) {
            @Override
            protected UserDetails getUserDetails(jakarta.servlet.http.HttpServletRequest request) {
                return user;
            }
        };
        when(orderService.createSubscriptionCheckout(
            eq((byte) 4), eq(101L), eq("BASIC_MONTHLY"), eq("checkout_0001"),
            any(ReadingSubscriptionCheckoutOptions.class)))
            .thenReturn(new ReadingSubscriptionCheckoutCreation(
                123L, 49_000, new Date(), false));

        var result = controller.createCheckout(new ReadingSubscriptionCheckoutRequest(
            "BASIC_MONTHLY", (byte) 4, "checkout_0001"), new MockHttpServletRequest());

        verify(orderService).createSubscriptionCheckout(
            eq((byte) 4), eq(101L), eq("BASIC_MONTHLY"), eq("checkout_0001"),
            any(ReadingSubscriptionCheckoutOptions.class));
        org.assertj.core.api.Assertions.assertThat(result.getData().paymentUrl())
            .isEqualTo("https://pay.example/checkout");
    }

    @Test
    void mandateCreationUsesAuthenticatedUserAndClientRequestId() {
        when(mandateService.create(eq(101L), eq("MONTHLY"), eq(3L),
            eq("mandate_0001"), anyString(), any()))
            .thenReturn(new VnpayRecurringMandateInitialization("NP123", "666821925535879168",
                "https://sandbox.vnpayment.vn/recurring-payment/pay", "VNPAYREC", "data-key"));
        when(mandateService.getState(101L)).thenReturn(
            new VnpayRecurringMandateState(true, "PENDING", "mandate_0001", "MONTHLY", 3L));

        var created = controller.createVnpayMandate(
            new VnpayRecurringMandateRequest("MONTHLY", 3L, "mandate_0001"),
            new MockHttpServletRequest());
        var state = controller.getVnpayMandateState(new MockHttpServletRequest());

        assertThat(created.getData().ispTxnId()).isEqualTo("666821925535879168");
        assertThat(state.getData().configured()).isTrue();
        assertThat(state.getData().clientRequestId()).isEqualTo("mandate_0001");
    }

    @Test
    void renewalMutationMapsOptimisticFailureToBusinessResponse() {
        when(service.cancelAtPeriodEnd(101L, 77L, 4L))
            .thenThrow(new IllegalStateException("concurrent"));

        assertThatThrownBy(() -> controller.cancelAtPeriodEnd(
            77L, new ReadingSubscriptionCancelRequest(4L), new MockHttpServletRequest()))
            .isInstanceOf(BusinessException.class);
    }

    private VnpayProperties configuredVnpay() {
        VnpayProperties vnpay = new VnpayProperties();
        vnpay.setEnabled(true);
        vnpay.setTmnCode("DEMOV210");
        vnpay.setHashSecret("test-secret");
        vnpay.setPayUrl("https://pay.example/create");
        vnpay.setReturnUrl("https://merchant.example/pay/vnpay/return");
        vnpay.setReconciliationEnabled(false);
        return vnpay;
    }
}
