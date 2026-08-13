package com.java2nb.novel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java2nb.novel.core.config.VnpayProperties;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

class VnpayQueryServiceTest {

    private static final List<String> RESPONSE_HASH_FIELDS = List.of(
        "vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode",
        "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo",
        "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode",
        "vnp_PromotionAmount"
    );
    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    private VnpayProperties properties;
    private VnpayService signingService;
    private ObjectMapper objectMapper;
    private PayOrderSnapshot order;
    private HttpServer server;

    @BeforeEach
    void setUp() {
        properties = new VnpayProperties();
        properties.setEnabled(true);
        properties.setTmnCode("DEMOV210");
        properties.setHashSecret("query-test-secret");
        properties.setPayUrl("https://sandbox.vnpayment.vn/paymentv2/vpcpay.html");
        properties.setReturnUrl("https://merchant.example/pay/vnpay/return");
        signingService = new VnpayService(properties);
        objectMapper = new ObjectMapper();
        order = new PayOrderSnapshot(1L, 123L, 10_000, 1_000,
            Date.from(Instant.parse("2026-07-16T05:00:00Z")), new Date());
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void verifiesSignedSuccessfulQueryResponseAndRejectsTampering() throws Exception {
        VnpayQueryService queryService = new VnpayQueryService(properties, signingService, objectMapper);
        Map<String, String> response = response("00");

        assertThat(queryService.parseResponse(order, objectMapper.writeValueAsString(response)).status())
            .isEqualTo(VnpayQueryResult.Status.SUCCESS);

        response.put("vnp_Amount", "3000000");
        assertThat(queryService.parseResponse(order, objectMapper.writeValueAsString(response)).status())
            .isEqualTo(VnpayQueryResult.Status.UNAVAILABLE);
    }

    @Test
    void mapsPendingAndFinalFailureTransactionStatuses() throws Exception {
        VnpayQueryService queryService = new VnpayQueryService(properties, signingService, objectMapper);

        assertThat(queryService.parseResponse(order, objectMapper.writeValueAsString(response("01"))).status())
            .isEqualTo(VnpayQueryResult.Status.PENDING);
        assertThat(queryService.parseResponse(order, objectMapper.writeValueAsString(response("02"))).status())
            .isEqualTo(VnpayQueryResult.Status.FAILED);
    }

    @Test
    void sendsAValidQueryDrJsonRequestOverHttp() throws Exception {
        AtomicReference<Map<String, String>> receivedRequest = new AtomicReference<>();
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/query", exchange -> {
            receivedRequest.set(objectMapper.readValue(exchange.getRequestBody(), MAP_TYPE));
            byte[] body = objectMapper.writeValueAsBytes(response("00"));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        properties.setQueryUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/query");
        VnpayQueryService queryService = new VnpayQueryService(properties, signingService, objectMapper);

        VnpayQueryResult result = queryService.query(order);

        assertThat(result.status()).isEqualTo(VnpayQueryResult.Status.SUCCESS);
        Map<String, String> request = receivedRequest.get();
        assertThat(request)
            .containsEntry("vnp_Command", "querydr")
            .containsEntry("vnp_TxnRef", "123")
            .containsEntry("vnp_TransactionDate", "20260716120000");
        String secureHash = request.remove("vnp_SecureHash");
        assertThat(signingService.verifyHash(String.join("|", request.values()), secureHash)).isTrue();
    }

    private Map<String, String> response(String transactionStatus) {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("vnp_ResponseId", "response-123");
        response.put("vnp_Command", "querydr");
        response.put("vnp_ResponseCode", "00");
        response.put("vnp_Message", "Success");
        response.put("vnp_TmnCode", "DEMOV210");
        response.put("vnp_TxnRef", "123");
        response.put("vnp_Amount", "1000000");
        response.put("vnp_BankCode", "NCB");
        response.put("vnp_PayDate", "20260716120500");
        response.put("vnp_TransactionNo", "456");
        response.put("vnp_TransactionType", "01");
        response.put("vnp_TransactionStatus", transactionStatus);
        response.put("vnp_OrderInfo", "Nap Xu Khởi Thư 123");
        response.put("vnp_PromotionCode", "");
        response.put("vnp_PromotionAmount", "");
        String hashData = RESPONSE_HASH_FIELDS.stream()
            .map(field -> response.getOrDefault(field, ""))
            .collect(Collectors.joining("|"));
        response.put("vnp_SecureHash", signingService.sign(hashData));
        return response;
    }
}
