package com.java2nb.novel.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java2nb.novel.core.config.VnpayRecurringProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class VnpayRecurringClient {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

    private final VnpayRecurringProperties properties;
    private final VnpayRecurringSigner signer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private volatile AccessToken cachedToken;

    private static final Set<String> AMBIGUOUS_RESPONSE_CODES = Set.of("01", "06", "99");
    private static final Set<String> CANCELLED_RESPONSE_CODES = Set.of("00", "04", "12");

    @Autowired
    public VnpayRecurringClient(VnpayRecurringProperties properties, VnpayRecurringSigner signer,
                                ObjectMapper objectMapper) {
        this(properties, signer, objectMapper, HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
            .build());
    }

    VnpayRecurringClient(VnpayRecurringProperties properties, VnpayRecurringSigner signer,
                         ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.signer = signer;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public VnpayRecurringMandateInitialization initializeMandate(VnpayRecurringMandateCommand command) {
        requireConfigured();
        try {
            Map<String, Object> payload = buildMandatePayload(command);
            HttpRequest request = HttpRequest.newBuilder(properties.endpoint("/recurring-payment/execute"))
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", bearerToken())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload),
                    StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new VnpayRecurringUnavailableException("VNPAY Recurring trả HTTP không thành công");
            }
            return parseMandateResponse(command, response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new VnpayRecurringUnavailableException("Yêu cầu VNPAY Recurring bị gián đoạn", exception);
        } catch (VnpayRecurringRejectedException | VnpayRecurringUnavailableException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new VnpayRecurringUnavailableException("Không thể khởi tạo ủy quyền VNPAY Recurring", exception);
        }
    }

    public VnpayRecurringChargeResult charge(VnpayRecurringChargeCommand command) {
        requireConfigured();
        try {
            Map<String, Object> payload = buildChargePayload(command);
            HttpRequest request = HttpRequest.newBuilder(properties.endpoint("/recurring-payment/execute"))
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", bearerToken())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload),
                    StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return VnpayRecurringChargeResult.pending("HTTP_" + response.statusCode());
            }
            return parseChargeResponse(command, response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return VnpayRecurringChargeResult.pending("INTERRUPTED");
        } catch (Exception exception) {
            return VnpayRecurringChargeResult.pending("UNAVAILABLE");
        }
    }

    public VnpayRecurringCancelResult cancel(VnpayRecurringCancelCommand command) {
        requireConfigured();
        try {
            Map<String, Object> payload = buildCancelPayload(command);
            HttpRequest request = HttpRequest.newBuilder(properties.endpoint("/recurring-payment/execute"))
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .header("Authorization", bearerToken())
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload),
                    StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                return VnpayRecurringCancelResult.retry("HTTP_" + response.statusCode());
            }
            return parseCancelResponse(response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return VnpayRecurringCancelResult.retry("INTERRUPTED");
        } catch (Exception exception) {
            return VnpayRecurringCancelResult.retry("UNAVAILABLE");
        }
    }

    Map<String, Object> buildCancelPayload(VnpayRecurringCancelCommand command) {
        Map<String, Object> transaction = Map.of("recurringId", command.providerRecurringId());
        Map<String, Object> token = Map.of("tokenId", command.providerToken());
        List<Object> fields = List.of(command.requestId(), "cancel_recurring",
            properties.getTmnCode(), command.providerToken(), command.providerRecurringId(), "",
            command.ipAddress(), command.userAgent(), properties.getVersion());
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reqId", command.requestId());
        payload.put("command", "cancel_recurring");
        payload.put("tmnCode", properties.getTmnCode());
        payload.put("transaction", transaction);
        payload.put("token", token);
        payload.put("ipAddr", command.ipAddress());
        payload.put("userAgent", command.userAgent());
        payload.put("addData", "");
        payload.put("version", properties.getVersion());
        payload.put("secureHash", signer.signFields(fields));
        return payload;
    }

    VnpayRecurringCancelResult parseCancelResponse(String responseBody) throws Exception {
        JsonNode response = objectMapper.readTree(responseBody);
        String responseCode = text(response, "rspCode");
        return CANCELLED_RESPONSE_CODES.contains(responseCode)
            ? VnpayRecurringCancelResult.revoked(responseCode)
            : VnpayRecurringCancelResult.retry(responseCode.isBlank() ? "INVALID_RESPONSE" : responseCode);
    }

    Map<String, Object> buildChargePayload(VnpayRecurringChargeCommand command) {
        long amount = Math.multiplyExact(command.amountVnd(), 100L);
        String orderInfo = "Gia han goi doc Khởi Thư " + command.orderReference();
        String recurringDate = command.recurringDate().format(DATE);
        String merchantDate = command.merchantDate().format(DATE_TIME);
        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderReference", command.orderReference());
        order.put("orderInfo", orderInfo);
        Map<String, Object> transaction = new LinkedHashMap<>();
        transaction.put("recurringId", command.providerRecurringId());
        transaction.put("recurringAmount", amount);
        transaction.put("recurringDate", recurringDate);
        transaction.put("currCode", "VND");
        transaction.put("mcDate", merchantDate);
        Map<String, Object> token = Map.of("tokenId", command.providerToken());
        List<Object> fields = List.of(command.requestId(), "recurring_pay",
            command.orderReference(), orderInfo, properties.getTmnCode(), command.providerToken(),
            command.providerRecurringId(), amount, recurringDate, "VND", "",
            properties.getVersion(), merchantDate);
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reqId", command.requestId());
        payload.put("command", "recurring_pay");
        payload.put("tmnCode", properties.getTmnCode());
        payload.put("order", order);
        payload.put("transaction", transaction);
        payload.put("token", token);
        payload.put("addData", "");
        payload.put("version", properties.getVersion());
        payload.put("secureHash", signer.signFields(fields));
        return payload;
    }

    VnpayRecurringChargeResult parseChargeResponse(VnpayRecurringChargeCommand command,
                                                    String responseBody) throws Exception {
        JsonNode response = objectMapper.readTree(responseBody);
        String responseCode = text(response, "rspCode");
        String responseMessage = text(response, "rspMsg");
        String addData = text(response, "addData");
        JsonNode transaction = response.path("transaction");
        String transactionId = text(transaction, "id");
        long amount = transaction.path("amount").asLong();
        long feeAmount = transaction.path("feeAmount").asLong();
        String currency = text(transaction, "currCode");
        String secureHash = text(response, "secureHash");
        if (!signer.verifyFields(List.of(responseCode, responseMessage, transactionId,
            amount, feeAmount, currency, addData), secureHash)) {
            return VnpayRecurringChargeResult.pending("INVALID_SIGNATURE");
        }
        if (!"00".equals(responseCode)) {
            return AMBIGUOUS_RESPONSE_CODES.contains(responseCode)
                ? VnpayRecurringChargeResult.pending(responseCode)
                : VnpayRecurringChargeResult.failed(responseCode);
        }
        long expectedAmount = Math.multiplyExact(command.amountVnd(), 100L);
        if (!transactionId.matches("[0-9]{8,18}") || amount != expectedAmount
            || !"VND".equals(currency)) {
            return VnpayRecurringChargeResult.pending("INVALID_SETTLEMENT");
        }
        return VnpayRecurringChargeResult.settled(transactionId);
    }

    Map<String, Object> buildMandatePayload(VnpayRecurringMandateCommand command) {
        long amount = Math.multiplyExact(command.recurringAmountVnd(), 100L);
        String orderInfo = "Dang ky gia han goi doc Khởi Thư";
        String firstDate = command.firstRenewalDate().format(DATE);
        String merchantDate = command.merchantDate().format(DATE_TIME);

        Map<String, Object> order = new LinkedHashMap<>();
        order.put("orderReference", command.merchantReference());
        order.put("orderInfo", orderInfo);
        order.put("orderType", properties.getOrderType());

        Map<String, Object> transaction = new LinkedHashMap<>();
        transaction.put("recurringAmount", amount);
        transaction.put("recurringFrequencyNumber", command.frequencyMonths());
        transaction.put("recurringFrequency", "month");
        transaction.put("recurringNumber", 0);
        transaction.put("recurringDate", firstDate);
        transaction.put("recurringStartDate", firstDate);
        transaction.put("recurringEndDate", "99991231");
        transaction.put("amount", 0);
        transaction.put("currCode", "VND");
        transaction.put("returnUrl", properties.getReturnUrl());
        transaction.put("cancelUrl", properties.getCancelUrl());
        transaction.put("mcDate", merchantDate);

        Map<String, Object> app = Map.of("userId", String.valueOf(command.userId()));
        Map<String, Object> customerInfo = Map.of("forename", "", "surname", "");
        List<Object> checksumFields = new ArrayList<>();
        checksumFields.add(command.requestId());
        checksumFields.add("recurring");
        checksumFields.add(command.merchantReference());
        checksumFields.add(orderInfo);
        checksumFields.add(properties.getOrderType());
        checksumFields.add(properties.getTmnCode());
        checksumFields.add(amount);
        checksumFields.add(command.frequencyMonths());
        checksumFields.add("month");
        checksumFields.add(0);
        checksumFields.add(firstDate);
        checksumFields.add(firstDate);
        checksumFields.add("99991231");
        checksumFields.add(0);
        checksumFields.add("VND");
        checksumFields.add("");
        checksumFields.add(command.userId());
        checksumFields.add("");
        checksumFields.add("");
        checksumFields.add(command.ipAddress());
        checksumFields.add(command.userAgent());
        checksumFields.add(properties.getReturnUrl());
        checksumFields.add(properties.getCancelUrl());
        checksumFields.add(properties.getVersion());
        checksumFields.add(properties.getLocale());
        checksumFields.add(merchantDate);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("reqId", command.requestId());
        payload.put("command", "recurring");
        payload.put("tmnCode", properties.getTmnCode());
        payload.put("order", order);
        payload.put("transaction", transaction);
        payload.put("customerInfo", customerInfo);
        payload.put("ipAddr", command.ipAddress());
        payload.put("userAgent", command.userAgent());
        payload.put("addData", "");
        payload.put("version", properties.getVersion());
        payload.put("locale", properties.getLocale());
        payload.put("app", app);
        payload.put("secureHash", signer.signFields(checksumFields));
        return payload;
    }

    VnpayRecurringMandateInitialization parseMandateResponse(VnpayRecurringMandateCommand command,
                                                              String responseBody) throws Exception {
        JsonNode response = objectMapper.readTree(responseBody);
        String responseCode = text(response, "rspCode");
        if (!"00".equals(responseCode)) {
            throw new VnpayRecurringRejectedException(responseCode);
        }
        JsonNode transaction = response.path("transaction");
        String transactionId = text(transaction, "id");
        long amount = transaction.path("amount").asLong();
        long feeAmount = transaction.path("feeAmount").asLong();
        String currency = text(transaction, "currCode");
        String responseMessage = text(response, "rspMsg");
        String addData = text(response, "addData");
        String dataKey = text(response, "dataKey");
        String secureHash = text(response, "secureHash");
        if (!transactionId.matches("[0-9]{8,18}") || dataKey.isBlank() || dataKey.length() > 2000
            || !"VND".equals(currency)
            || !signer.verifyFields(List.of(responseCode, responseMessage, transactionId, amount,
                feeAmount, currency, addData, dataKey), secureHash)) {
            throw new VnpayRecurringUnavailableException("Phản hồi VNPAY Recurring không hợp lệ");
        }
        return new VnpayRecurringMandateInitialization(command.merchantReference(), transactionId,
            properties.getPayUrl(), properties.getTmnCode(), dataKey);
    }

    private synchronized String bearerToken() throws Exception {
        Instant now = Instant.now();
        if (cachedToken != null && cachedToken.expiresAt().isAfter(now.plusSeconds(30))) {
            return cachedToken.authorization();
        }
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("clientId", properties.getClientId());
        payload.put("username", properties.getUsername());
        payload.put("password", properties.getPassword());
        payload.put("clientSecret", properties.getClientSecret());
        HttpRequest request = HttpRequest.newBuilder(properties.endpoint("/oauth/authenticate"))
            .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload),
                StandardCharsets.UTF_8))
            .build();
        HttpResponse<String> response = httpClient.send(request,
            HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new VnpayRecurringUnavailableException("Không thể xác thực VNPAY Recurring");
        }
        JsonNode body = objectMapper.readTree(response.body());
        JsonNode data = body.path("data");
        String accessToken = text(data, "accessToken");
        String tokenType = text(data, "tokenType");
        long expiresIn = data.path("expiresIn").asLong();
        if (!"00".equals(text(body, "rspCode")) || accessToken.isBlank()
            || tokenType.isBlank() || expiresIn <= 30) {
            throw new VnpayRecurringUnavailableException("Phản hồi xác thực VNPAY Recurring không hợp lệ");
        }
        cachedToken = new AccessToken(tokenType + " " + accessToken, now.plusSeconds(expiresIn));
        return cachedToken.authorization();
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new VnpayRecurringUnavailableException("VNPAY Recurring chưa được cấu hình");
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? "" : value.asText();
    }

    private record AccessToken(String authorization, Instant expiresAt) {
    }
}
