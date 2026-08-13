package com.java2nb.novel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java2nb.novel.core.config.VnpayRecurringProperties;
import com.java2nb.novel.service.subscription.ReadingSubscriptionProviderQuery;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class VnpayRecurringQueryServiceTest {

    private static final TypeReference<Map<String, String>> MAP_TYPE = new TypeReference<>() {
    };

    private VnpayRecurringProperties properties;
    private VnpayRecurringSigner signer;
    private ObjectMapper objectMapper;
    private ReadingSubscriptionProviderQuery query;
    private HttpServer server;

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
        properties.setReturnUrl("https://khoithu.vn/return");
        properties.setCancelUrl("https://khoithu.vn/cancel");
        properties.setQueryUrl("https://sandbox.vnpayment.vn/merchant_webapi/api/transaction");
        properties.setServerIp("127.0.0.1");
        signer = new VnpayRecurringSigner(properties);
        objectMapper = new ObjectMapper();
        query = new ReadingSubscriptionProviderQuery(81L, 1, "NPR81A1", 49_000L,
            Date.from(Instant.parse("2026-08-08T05:00:00Z")));
    }

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
        }
    }

    @Test
    void verifiesSignedMatchingResponseAndMapsProviderStatus() throws Exception {
        VnpayRecurringQueryService service = new VnpayRecurringQueryService(
            properties, signer, objectMapper);

        assertThat(service.parseResponse(query, response("00")).status())
            .isEqualTo(VnpayQueryResult.Status.SUCCESS);
        assertThat(service.parseResponse(query, response("01")).status())
            .isEqualTo(VnpayQueryResult.Status.PENDING);
        assertThat(service.parseResponse(query, response("02")).status())
            .isEqualTo(VnpayQueryResult.Status.FAILED);
        assertThat(service.parseResponse(query, response("04")).status())
            .isEqualTo(VnpayQueryResult.Status.PENDING);
        assertThat(service.parseResponse(query, response("07")).status())
            .isEqualTo(VnpayQueryResult.Status.PENDING);
        assertThat(service.parseResponse(query, response("09")).status())
            .isEqualTo(VnpayQueryResult.Status.PENDING);
        assertThat(service.parseResponse(query, response("00").replace("4900000", "4800000")).status())
            .isEqualTo(VnpayQueryResult.Status.UNAVAILABLE);
    }

    @Test
    void sendsDocumentedRecurringQueryDrRequest() throws Exception {
        AtomicReference<Map<String, String>> receivedRequest = new AtomicReference<>();
        byte[] responseBody = response("00").getBytes(java.nio.charset.StandardCharsets.UTF_8);
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/query", exchange -> {
            receivedRequest.set(objectMapper.readValue(exchange.getRequestBody(), MAP_TYPE));
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, responseBody.length);
            exchange.getResponseBody().write(responseBody);
            exchange.close();
        });
        server.start();
        properties.setQueryUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/query");
        VnpayRecurringQueryService service = new VnpayRecurringQueryService(
            properties, signer, objectMapper);

        assertThat(service.query(query).status()).isEqualTo(VnpayQueryResult.Status.SUCCESS);
        Map<String, String> request = receivedRequest.get();
        assertThat(request)
            .containsEntry("vnp_Command", "querydr")
            .containsEntry("vnp_TmnCode", "VNPAYREC")
            .containsEntry("vnp_TxnRef", "NPR81A1")
            .containsEntry("vnp_TransactionDate", "20260808120000");
        String secureHash = request.remove("vnp_SecureHash");
        assertThat(signer.verifyFields(List.copyOf(request.values()), secureHash)).isTrue();
    }

    private String response(String transactionStatus) throws Exception {
        Map<String, String> response = new LinkedHashMap<>();
        response.put("vnp_ResponseId", "response-81");
        response.put("vnp_Command", "querydr");
        response.put("vnp_ResponseCode", "00");
        response.put("vnp_Message", "Success");
        response.put("vnp_TmnCode", "VNPAYREC");
        response.put("vnp_TxnRef", "NPR81A1");
        response.put("vnp_Amount", "4900000");
        response.put("vnp_BankCode", "NCB");
        response.put("vnp_PayDate", "20260808120500");
        response.put("vnp_TransactionNo", "666821925535879168");
        response.put("vnp_TransactionType", "01");
        response.put("vnp_TransactionStatus", transactionStatus);
        response.put("vnp_OrderInfo", "Tra soat gia han Khởi Thư NPR81A1");
        response.put("vnp_PromotionCode", "");
        response.put("vnp_PromotionAmount", "");
        response.put("vnp_SecureHash", signer.signFields(List.copyOf(response.values())));
        return objectMapper.writeValueAsString(response);
    }
}
