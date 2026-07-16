package com.java2nb.novel.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.java2nb.novel.core.config.VnpayProperties;
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
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class VnpayQueryService {

    private static final DateTimeFormatter VNPAY_DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final TypeReference<Map<String, Object>> RESPONSE_TYPE = new TypeReference<>() {
    };
    private static final List<String> RESPONSE_HASH_FIELDS = List.of(
        "vnp_ResponseId", "vnp_Command", "vnp_ResponseCode", "vnp_Message", "vnp_TmnCode",
        "vnp_TxnRef", "vnp_Amount", "vnp_BankCode", "vnp_PayDate", "vnp_TransactionNo",
        "vnp_TransactionType", "vnp_TransactionStatus", "vnp_OrderInfo", "vnp_PromotionCode",
        "vnp_PromotionAmount"
    );

    private final VnpayProperties properties;
    private final VnpayService vnpayService;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired
    public VnpayQueryService(VnpayProperties properties, VnpayService vnpayService, ObjectMapper objectMapper) {
        this(properties, vnpayService, objectMapper, HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build());
    }

    VnpayQueryService(VnpayProperties properties, VnpayService vnpayService, ObjectMapper objectMapper,
                      HttpClient httpClient) {
        this.properties = properties;
        this.vnpayService = vnpayService;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public VnpayQueryResult query(PayOrderSnapshot order) {
        try {
            ZoneId zoneId = ZoneId.of(properties.getTimeZone());
            String requestId = UUID.randomUUID().toString().replace("-", "");
            String transactionDate = LocalDateTime.ofInstant(order.createTime().toInstant(), zoneId)
                .format(VNPAY_DATE_FORMAT);
            String createDate = LocalDateTime.now(zoneId).format(VNPAY_DATE_FORMAT);
            String orderInfo = "Truy van giao dich " + order.outTradeNo();

            Map<String, String> requestData = new LinkedHashMap<>();
            requestData.put("vnp_RequestId", requestId);
            requestData.put("vnp_Version", properties.getVersion());
            requestData.put("vnp_Command", "querydr");
            requestData.put("vnp_TmnCode", properties.getTmnCode());
            requestData.put("vnp_TxnRef", String.valueOf(order.outTradeNo()));
            requestData.put("vnp_TransactionDate", transactionDate);
            requestData.put("vnp_CreateDate", createDate);
            requestData.put("vnp_IpAddr", properties.getServerIp());
            requestData.put("vnp_OrderInfo", orderInfo);
            requestData.put("vnp_SecureHash", vnpayService.sign(String.join("|", requestData.values())));

            HttpRequest request = HttpRequest.newBuilder(URI.create(properties.getQueryUrl()))
                .timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(requestData),
                    StandardCharsets.UTF_8))
                .build();
            HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                log.warn("QueryDr VNPAY trả HTTP {} cho đơn {}", response.statusCode(), order.outTradeNo());
                return new VnpayQueryResult(VnpayQueryResult.Status.UNAVAILABLE, null);
            }
            return parseResponse(order, response.body());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            log.warn("QueryDr VNPAY bị gián đoạn cho đơn {}", order.outTradeNo());
            return new VnpayQueryResult(VnpayQueryResult.Status.UNAVAILABLE, null);
        } catch (Exception exception) {
            log.warn("Không thể QueryDr VNPAY cho đơn {}: {}", order.outTradeNo(), exception.getMessage());
            return new VnpayQueryResult(VnpayQueryResult.Status.UNAVAILABLE, null);
        }
    }

    VnpayQueryResult parseResponse(PayOrderSnapshot order, String responseBody) throws Exception {
        Map<String, Object> rawResponse = objectMapper.readValue(responseBody, RESPONSE_TYPE);
        Map<String, String> response = rawResponse.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getKey,
                entry -> entry.getValue() == null ? "" : String.valueOf(entry.getValue()),
                (first, second) -> first, LinkedHashMap::new));
        String hashData = RESPONSE_HASH_FIELDS.stream()
            .map(field -> response.getOrDefault(field, ""))
            .collect(Collectors.joining("|"));
        if (!vnpayService.verifyHash(hashData, response.get("vnp_SecureHash"))) {
            return new VnpayQueryResult(VnpayQueryResult.Status.UNAVAILABLE, null);
        }
        String command = response.get("vnp_Command");
        long expectedAmount = Math.multiplyExact((long) order.totalAmount(), 100L);
        if (!properties.getTmnCode().equals(response.get("vnp_TmnCode"))
            || (command != null && !command.isBlank() && !"querydr".equals(command))
            || !String.valueOf(order.outTradeNo()).equals(response.get("vnp_TxnRef"))
            || !String.valueOf(expectedAmount).equals(response.get("vnp_Amount"))
            || !"00".equals(response.get("vnp_ResponseCode"))) {
            return new VnpayQueryResult(VnpayQueryResult.Status.UNAVAILABLE, null);
        }

        String transactionStatus = response.get("vnp_TransactionStatus");
        String transactionType = response.get("vnp_TransactionType");
        String tradeNo = response.get("vnp_TransactionNo");
        if (transactionType != null && !transactionType.isBlank() && !"01".equals(transactionType)) {
            return new VnpayQueryResult(VnpayQueryResult.Status.UNAVAILABLE, null);
        }
        if ("00".equals(transactionStatus) && tradeNo != null && !tradeNo.isBlank()) {
            return new VnpayQueryResult(VnpayQueryResult.Status.SUCCESS, tradeNo);
        }
        if ("02".equals(transactionStatus)) {
            return new VnpayQueryResult(VnpayQueryResult.Status.FAILED, tradeNo);
        }
        if ("01".equals(transactionStatus)) {
            return new VnpayQueryResult(VnpayQueryResult.Status.PENDING, tradeNo);
        }
        return new VnpayQueryResult(VnpayQueryResult.Status.UNAVAILABLE, tradeNo);
    }
}
