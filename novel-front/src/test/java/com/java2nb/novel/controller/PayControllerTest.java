package com.java2nb.novel.controller;

import com.java2nb.novel.core.config.VnpayProperties;
import com.java2nb.novel.core.config.VietQrProperties;
import com.java2nb.novel.core.i18n.Messages;
import com.java2nb.novel.core.observability.NovelBusinessMetrics;
import com.java2nb.novel.core.payment.PaymentAdapterFactory;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.service.OrderService;
import com.java2nb.novel.service.PayOrderCreation;
import com.java2nb.novel.service.PayOrderState;
import com.java2nb.novel.service.PayOrderUpdateResult;
import com.java2nb.novel.service.VnpayService;
import com.java2nb.novel.service.payment.impl.VietQrPaymentAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyByte;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PayControllerTest {

    private static final String HASH_SECRET = "test-vnpay-secret";

    private VnpayProperties properties;
    private VietQrProperties vietQrProperties;
    private OrderService orderService;
    private Messages messages;
    private PayController controller;

    @BeforeEach
    void setUp() {
        properties = new VnpayProperties();
        properties.setEnabled(true);
        properties.setTmnCode("DEMOV210");
        properties.setHashSecret(HASH_SECRET);
        properties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("https://merchant.example/pay/vnpay/return");
        vietQrProperties = configuredVietQrProperties();
        orderService = mock(OrderService.class);
        messages = mock(Messages.class);
        when(messages.get(any())).thenAnswer(invocation -> invocation.getArgument(0));
        controller = controller(new PaymentAdapterFactory(List.of(
            new VietQrPaymentAdapter(vietQrProperties))), null);
    }

    @Test
    void acceptsValidSuccessfulIpnAndCreditsExpectedAmount() {
        when(orderService.processPayOrder(123L, "456", (byte) 4, 10_000, true))
            .thenReturn(PayOrderUpdateResult.SUCCESS);

        Map<String, String> response = controller.vnpayIpn(validIpnRequest());

        assertThat(response).containsEntry("RspCode", "00");
        verify(orderService).processPayOrder(123L, "456", (byte) 4, 10_000, true);
    }

    @Test
    void rejectsInvalidChecksumBeforeReadingOrder() {
        MockHttpServletRequest request = validIpnRequest();
        request.setParameter("vnp_SecureHash", "00");

        Map<String, String> response = controller.vnpayIpn(request);

        assertThat(response).containsEntry("RspCode", "97");
        verify(orderService, never()).processPayOrder(any(), any(), anyByte(), anyInt(), anyBoolean());
    }

    @Test
    void mapsDuplicateAndAmountMismatchToVnpayResponseCodes() {
        when(orderService.processPayOrder(123L, "456", (byte) 4, 10_000, true))
            .thenReturn(PayOrderUpdateResult.ALREADY_PROCESSED, PayOrderUpdateResult.INVALID_AMOUNT);

        assertThat(controller.vnpayIpn(validIpnRequest())).containsEntry("RspCode", "02");
        assertThat(controller.vnpayIpn(validIpnRequest())).containsEntry("RspCode", "04");
    }

    @Test
    void acceptsIpnForAnOrderCreatedBeforeAllowedAmountsChange() {
        when(orderService.processPayOrder(123L, "456", (byte) 4, 10_000, true))
            .thenReturn(PayOrderUpdateResult.SUCCESS);
        properties.setAllowedAmountsVnd(List.of(30_000));

        assertThat(controller.vnpayIpn(validIpnRequest())).containsEntry("RspCode", "00");
    }

    @Test
    void returnUrlShowsProcessingUntilIpnHasConfirmedTheStoredOrder() throws Exception {
        when(orderService.inspectPayOrder(123L, (byte) 4, 10_000)).thenReturn(PayOrderState.PENDING);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.vnpayReturn(validIpnRequest(), response);

        assertThat(response.getRedirectedUrl()).isEqualTo("/pay/index.html?payment=vnpay-processing");
    }

    @Test
    void returnUrlRejectsSignedDataThatDoesNotMatchAStoredOrder() throws Exception {
        when(orderService.inspectPayOrder(123L, (byte) 4, 10_000)).thenReturn(PayOrderState.INVALID_AMOUNT);
        MockHttpServletResponse response = new MockHttpServletResponse();

        controller.vnpayReturn(validIpnRequest(), response);

        assertThat(response.getRedirectedUrl()).isEqualTo("/pay/index.html?payment=vnpay-failed");
    }

    @Test
    void statusEndpointInspectsTheStoredOrderWithoutTrustingAClientAmount() {
        when(orderService.inspectPayOrder(123L, (byte) 4, 0)).thenReturn(PayOrderState.SUCCESS);

        assertThat(controller.queryStatus(123L))
            .containsEntry("outTradeNo", 123L)
            .containsEntry("status", "SUCCESS");
        verify(orderService).inspectPayOrder(123L, (byte) 4, 0);
    }

    @Test
    void jsonCheckoutUsesServerAmountAndReturnsSignedPaymentUrl() {
        UserDetails user = mock(UserDetails.class);
        when(user.getId()).thenReturn(11L);
        controller = controller(new PaymentAdapterFactory(List.of(
            new VietQrPaymentAdapter(vietQrProperties))), user);
        Date createTime = new Date();
        when(orderService.createPayOrder((byte) 4, 10_000, 1_000, 11L))
            .thenReturn(new PayOrderCreation(123L, createTime));
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pay/vnpay");
        request.addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        ResponseEntity<?> response = controller.vnpay(10_000, request);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getBody()).isInstanceOf(Map.class);
        Map<?, ?> body = (Map<?, ?>) response.getBody();
        assertThat(body.get("code")).isEqualTo(200);
        assertThat(body.get("paymentUrl").toString())
            .startsWith("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html?");
        verify(orderService).createPayOrder((byte) 4, 10_000, 1_000, 11L);
    }

    @Test
    void jsonCheckoutRejectsAmountOutsideServerAllowlist() {
        UserDetails user = mock(UserDetails.class);
        controller = controller(new PaymentAdapterFactory(List.of(
            new VietQrPaymentAdapter(vietQrProperties))), user);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pay/vnpay");
        request.addHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE);

        ResponseEntity<?> response = controller.vnpay(12_345, request);

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        verify(orderService, never()).createPayOrder(anyByte(), anyInt(), anyInt(), any());
    }

    @Test
    void browserFormCheckoutKeepsRedirectBehavior() {
        UserDetails user = mock(UserDetails.class);
        when(user.getId()).thenReturn(11L);
        controller = controller(new PaymentAdapterFactory(List.of(
            new VietQrPaymentAdapter(vietQrProperties))), user);
        when(orderService.createPayOrder((byte) 4, 10_000, 1_000, 11L))
            .thenReturn(new PayOrderCreation(123L, new Date()));

        ResponseEntity<?> response = controller.vnpay(10_000,
            new MockHttpServletRequest("POST", "/pay/vnpay"));

        assertThat(response.getStatusCode().value()).isEqualTo(302);
        assertThat(response.getHeaders().getLocation()).hasScheme("https");
    }

    @Test
    void browserFormErrorDoesNotChangeIntoJsonContract() {
        UserDetails user = mock(UserDetails.class);
        controller = controller(new PaymentAdapterFactory(List.of(
            new VietQrPaymentAdapter(vietQrProperties))), user);

        ResponseEntity<?> response = controller.vnpay(12_345,
            new MockHttpServletRequest("POST", "/pay/vnpay"));

        assertThat(response.getStatusCode().value()).isEqualTo(400);
        assertThat(response.getBody()).isEqualTo("payment.amount.invalid");
        verify(orderService, never()).createPayOrder(anyByte(), anyInt(), anyInt(), any());
    }

    @Test
    void vietQrWebhookFailsClosedWhenAdapterIsMissing() {
        controller = controller(new PaymentAdapterFactory(List.of()), null);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/pay/vietqr/webhook");
        request.addParameter("outTradeNo", "1002003004");
        request.addParameter("amount", "50000");

        Map<String, Object> response = controller.vietqrWebhook(request, null);

        assertThat(response).containsEntry("code", 503);
        verify(orderService, never()).processPayOrder(any(), any(), anyByte(), anyInt(), anyBoolean());
    }

    @Test
    void vietQrChannelAndOrderCreationStayDisabledWithoutSafeConfiguration() {
        vietQrProperties.setEnabled(false);
        UserDetails user = mock(UserDetails.class);
        when(user.getId()).thenReturn(11L);
        controller = controller(new PaymentAdapterFactory(List.of(
            new VietQrPaymentAdapter(vietQrProperties))), user);

        assertThat(controller.listChannels()).filteredOn(channel -> channel.get("code").equals(5))
            .singleElement().satisfies(channel -> assertThat(channel).containsEntry("enabled", false));
        assertThat(controller.vietqr(50_000, new MockHttpServletRequest()))
            .containsEntry("code", 503);
        verify(orderService, never()).createPayOrder(anyByte(), anyInt(), anyInt(), any());
    }

    @Test
    void vietQrOrderCreationUsesTheServerAmountAllowlist() {
        UserDetails user = mock(UserDetails.class);
        when(user.getId()).thenReturn(11L);
        controller = controller(new PaymentAdapterFactory(List.of(
            new VietQrPaymentAdapter(vietQrProperties))), user);

        assertThat(controller.vietqr(12_345, new MockHttpServletRequest()))
            .containsEntry("code", 400);
        verify(orderService, never()).createPayOrder(anyByte(), anyInt(), anyInt(), any());
    }

    private PayController controller(PaymentAdapterFactory factory, UserDetails user) {
        return new PayController(properties, new VnpayService(properties), orderService, messages,
            vietQrProperties, factory, new NovelBusinessMetrics(new SimpleMeterRegistry())) {
            @Override
            protected UserDetails getUserDetails(jakarta.servlet.http.HttpServletRequest request) {
                return user;
            }
        };
    }

    private VietQrProperties configuredVietQrProperties() {
        VietQrProperties configured = new VietQrProperties();
        configured.setEnabled(true);
        configured.setBankBin("970422");
        configured.setAccountNo("1234567890");
        configured.setAccountName("NOVEL PLUS");
        configured.setSecretToken("0123456789abcdef0123456789abcdef");
        return configured;
    }

    private MockHttpServletRequest validIpnRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/pay/vnpay/ipn");
        request.addParameter("vnp_Amount", "1000000");
        request.addParameter("vnp_ResponseCode", "00");
        request.addParameter("vnp_TmnCode", "DEMOV210");
        request.addParameter("vnp_TransactionNo", "456");
        request.addParameter("vnp_TransactionStatus", "00");
        request.addParameter("vnp_TxnRef", "123");
        String data = "vnp_Amount=1000000&vnp_ResponseCode=00&vnp_TmnCode=DEMOV210"
            + "&vnp_TransactionNo=456&vnp_TransactionStatus=00&vnp_TxnRef=123";
        request.addParameter("vnp_SecureHash", hmacSha512(data));
        return request;
    }

    private String hmacSha512(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA512");
            mac.init(new SecretKeySpec(HASH_SECRET.getBytes(StandardCharsets.UTF_8), "HmacSHA512"));
            StringBuilder result = new StringBuilder();
            for (byte value : mac.doFinal(data.getBytes(StandardCharsets.UTF_8))) {
                result.append(String.format("%02x", value));
            }
            return result.toString();
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
