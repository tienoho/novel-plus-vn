package com.java2nb.novel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java2nb.novel.core.config.VnpayRecurringProperties;
import com.java2nb.novel.service.subscription.ReadingSubscriptionProviderQuery;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VnpayRecurringQueryService {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final TypeReference<Map<String, Object>> RESPONSE_TYPE = new TypeReference<>() {
    };
    private static final List<String> RESPONSE_HASH_FIELDS = List.of(
        "vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode",
        "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo",
        "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode",
        "vnp_PromotionAmount"
    );
    private static final Set<String> PENDING_STATUSES = Set.of("01", "04", "05", "06", "07", "09");
    private static final Set<String> FAILED_STATUSES = Set.of("02");

    private final VnpayRecurringProperties properties;
    private final VnpayRecurringSigner signer;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public VnpayRecurringQueryService(VnpayRecurringProperties properties,
                                      VnpayRecurringSigner signer,
                                      ObjectMapper objectMapper) {
        this(properties, signer, objectMapper, HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(properties.getConnectTimeoutSeconds()))
            .build());
    }

    VnpayRecurringQueryService(VnpayRecurringProperties properties,
                               VnpayRecurringSigner signer,
                               ObjectMapper objectMapper,
                               HttpClient httpClient) {
        this.properties = properties;
        this.signer = signer;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public VnpayQueryResult query(ReadingSubscriptionProviderQuery providerQuery) {
        if (!properties.isConfigured()) {
            return unavailable();
        }
        try {
            ZoneId zoneId = ZoneId.of(properties.getTimeZone());
            String requestId = UUID.randomUUID().toString().replace("-", "");
            String transactionDate = LocalDateTime.ofInstant(
                providerQuery.merchantDate().toInstant(), zoneId).format(DATE_TIME);
            String createDate = LocalDateTime.now(zoneId).format(DATE_TIME);
            String orderInfo = "Tra soat gia han Khởi Thư " + providerQuery.providerRequestId();

            Map<String, String> requestData = new LinkedHashMap<>();
            requestData.put("vnp_RequestId", requestId);
            requestData.put("vnp_Version", properties.getVersion());
            requestData.put("vnp_Command", "querydr");
            requestData.put("vnp_TmnCode", properties.getTmnCode());
            requestData.put("vnp_TxnRef", providerQuery.providerRequestId());
            requestData.put("vnp_TransactionDate", transactionDate);
            requestData.put("vnp_CreateDate", createDate);
            requestData.put("vnp_IpAddr", properties.getServerIp());
            requestData.put("vnp_OrderInfo", orderInfo);
            requestData.put("vnp_SecureHash", signer.signFields(List.copyOf(requestData.values())));

            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getQueryUrl()))
                .timeout(Duration.ofSeconds(properties.getRequestTimeoutSeconds()))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestData),
                    StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("QueryDr VNPAY Recurring trả HTTP {} cho cycle {}",
                    response.statusCode(), providerQuery.cycleId());
                return unavailable();
            }
            return parseResponse(providerQuery, response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return unavailable();
        } catch (Exception exception) {
            log.warn("Không thể QueryDr VNPAY Recurring cho cycle {}: {}",
                providerQuery.cycleId(), exception.getMessage());
            return unavailable();
        }
    }

    VnpayQueryResult parseResponse(ReadingSubscriptionProviderQuery providerQuery,
                                   String responseBody) throws Exception {
        Map<String, Object> raw = objectMapper.readValue(responseBody, RESPONSE_TYPE);
        Map<String, String> response = raw.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue() == null ? "" : String.valueOf(entry.getValue()),
                (first, second) -> first, LinkedHashMap::new));
        List<String> signedFields = RESPONSE_HASH_FIELDS.stream()
            .map(field -> response.getOrDefault(field, ""))
            .toList();
        if (!signer.verifyFields(signedFields, response.get("vnp_SecureHash"))) {
            return unavailable("INVALID_SIGNATURE");
        }
        String command = response.get("vnp_Command");
        long expectedAmount = Math.multiplyExact(providerQuery.amountVnd(), 100L);
        if (!properties.getTmnCode().equals(response.get("vnp_TmnCode"))
            || (command != null && !command.isBlank() && !"querydr".equals(command))
            || !providerQuery.providerRequestId().equals(response.get("vnp_TxnRef"))
            || !String.valueOf(expectedAmount).equals(response.get("vnp_Amount"))
            || !"00".equals(response.get("vnp_ResponseCode"))) {
            return unavailable("INVALID_RESPONSE");
        }
        String transactionType = response.get("vnp_TransactionType");
        if (transactionType != null && !transactionType.isBlank() && !"01".equals(transactionType)) {
            return unavailable("INVALID_TRANSACTION_TYPE");
        }
        String status = response.get("vnp_TransactionStatus");
        String transactionNo = response.get("vnp_TransactionNo");
        if ("00".equals(status) && transactionNo != null && transactionNo.matches("[0-9]{1,18}")) {
            return new VnpayQueryResult(VnpayQueryResult.Status.SUCCESS, transactionNo, "TX_00");
        }
        if (PENDING_STATUSES.contains(status)) {
            return new VnpayQueryResult(VnpayQueryResult.Status.PENDING, transactionNo, "TX_" + status);
        }
        if (FAILED_STATUSES.contains(status)) {
            return new VnpayQueryResult(VnpayQueryResult.Status.FAILED, transactionNo, "TX_" + status);
        }
        return unavailable("UNKNOWN_TRANSACTION_STATUS");
    }

    private VnpayQueryResult unavailable() {
        return unavailable("UNAVAILABLE");
    }

    private VnpayQueryResult unavailable(String responseCode) {
        return new VnpayQueryResult(VnpayQueryResult.Status.UNAVAILABLE, null, responseCode);
    }
}
