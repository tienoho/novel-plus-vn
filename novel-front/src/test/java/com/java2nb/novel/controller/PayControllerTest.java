package com.java2nb.novel.controller;

import com.java2nb.novel.core.config.VnpayProperties;
import com.java2nb.novel.core.i18n.Messages;
import com.java2nb.novel.service.OrderService;
import com.java2nb.novel.service.PayOrderState;
import com.java2nb.novel.service.PayOrderUpdateResult;
import com.java2nb.novel.service.VnpayService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
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
    private OrderService orderService;
    private PayController controller;

    @BeforeEach
    void setUp() {
        properties = new VnpayProperties();
        properties.setEnabled(true);
        properties.setTmnCode("DEMOV210");
        properties.setHashSecret(HASH_SECRET);
        properties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("https://merchant.example/pay/vnpay/return");
        orderService = mock(OrderService.class);
        controller = new PayController(properties, new VnpayService(properties), orderService, mock(Messages.class));
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
