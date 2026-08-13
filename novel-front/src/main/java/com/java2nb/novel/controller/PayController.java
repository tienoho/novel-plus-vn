package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.config.VietQrProperties;
import com.java2nb.novel.core.config.VnpayProperties;
import com.java2nb.novel.core.i18n.Messages;
import com.java2nb.novel.core.observability.NovelBusinessMetrics;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.Outcome;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.PaymentOperation;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.PaymentProvider;
import com.java2nb.novel.core.utils.IpUtil;
import com.java2nb.novel.service.OrderService;
import com.java2nb.novel.service.PayOrderCreation;
import com.java2nb.novel.service.PayOrderState;
import com.java2nb.novel.service.PayOrderUpdateResult;
import com.java2nb.novel.service.VnpayService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import com.java2nb.novel.common.annotation.AuditLog;
import com.java2nb.novel.common.annotation.LimitType;
import com.java2nb.novel.common.annotation.RateLimit;
import com.java2nb.novel.core.payment.*;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.*;

@Controller
@RequestMapping("pay")
@RequiredArgsConstructor
@Slf4j
public class PayController extends BaseController {

    private static final byte VNPAY_CHANNEL = 4;
    private static final byte VIETQR_CHANNEL = 5;

    private final VnpayProperties vnpayProperties;
    private final VnpayService vnpayService;
    private final OrderService orderService;
    private final Messages messages;
    private final VietQrProperties vietQrProperties;
    private final PaymentAdapterFactory paymentAdapterFactory;
    private final NovelBusinessMetrics metrics;

    @ResponseBody
    @GetMapping("channels")
    public List<Map<String, Object>> listChannels() {
        List<Map<String, Object>> channels = new ArrayList<>();
        channels.add(Map.of("code", 4, "name", "VNPAY", "enabled", vnpayProperties.isConfigured()));
        channels.add(Map.of("code", 5, "name", "VIETQR", "enabled",
            vietQrProperties.isConfigured() && paymentAdapterFactory.findAdapter(VIETQR_CHANNEL).isPresent()));
        return channels;
    }

    @PostMapping("vnpay")
    @RateLimit(key = "vnpay", count = 10, timeWindowSeconds = 60, limitType = LimitType.USER)
    @AuditLog(module = "PAYMENT", eventType = "CREATE_VNPAY_ORDER", detail = "Tao don nap tien VNPAY")
    public ResponseEntity<?> vnpay(Integer payAmount, HttpServletRequest request) {
        boolean jsonResponse = acceptsJson(request);
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            if (jsonResponse) {
                return paymentError(HttpStatus.UNAUTHORIZED, messages.get("auth.login.required"), true);
            }
            return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create("/user/login.html?originUrl=/pay/index.html"))
                .build();
        }
        if (!vnpayProperties.isConfigured()) {
            return paymentError(HttpStatus.SERVICE_UNAVAILABLE, messages.get("payment.vnpay.unavailable"),
                jsonResponse);
        }
        if (payAmount == null || !vnpayProperties.isAllowedAmount(payAmount)) {
            return paymentError(HttpStatus.BAD_REQUEST, messages.get("payment.amount.invalid"), jsonResponse);
        }

        int accountAmount = vnpayProperties.calculateXu(payAmount);
        PayOrderCreation order = orderService.createPayOrder(VNPAY_CHANNEL, payAmount, accountAmount,
            userDetails.getId());
        String paymentUrl = vnpayService.createPaymentUrl(order.outTradeNo(), payAmount,
            IpUtil.getRealIp(request), order.createTime());
        if (jsonResponse) {
            return ResponseEntity.ok(Map.of("code", 200, "paymentUrl", paymentUrl));
        }
        return ResponseEntity.status(HttpStatus.FOUND).location(URI.create(paymentUrl)).build();
    }

    @ResponseBody
    @PostMapping("vietqr")
    @RateLimit(key = "vietqr", count = 10, timeWindowSeconds = 60, limitType = LimitType.USER)
    @AuditLog(module = "PAYMENT", eventType = "CREATE_VIETQR_ORDER", detail = "Tao don nap tien VietQR")
    public Map<String, Object> vietqr(@RequestParam("payAmount") Integer payAmount, HttpServletRequest request) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            return Map.of("code", 401, "msg", message("auth.login.required", "Vui lòng đăng nhập trước"));
        }
        PaymentAdapter vietQrAdapter = paymentAdapterFactory.findAdapter(VIETQR_CHANNEL).orElse(null);
        if (!vietQrProperties.isConfigured() || vietQrAdapter == null) {
            metrics.recordPayment(PaymentProvider.VIETQR, PaymentOperation.WEBHOOK, Outcome.UNAVAILABLE);
            return Map.of("code", 503, "msg", message("payment.vietqr.unavailable",
                "VietQR chưa được cấu hình an toàn"));
        }
        if (payAmount == null || !vnpayProperties.isAllowedAmount(payAmount)) {
            return Map.of("code", 400, "msg", message("payment.amount.invalid",
                "Mệnh giá nạp không hợp lệ"));
        }

        int accountAmount = vnpayProperties.calculateXu(payAmount);
        PayOrderCreation order = orderService.createPayOrder(VIETQR_CHANNEL, payAmount, accountAmount, userDetails.getId());

        PaymentCreationRequest creationRequest = PaymentCreationRequest.builder()
            .outTradeNo(order.outTradeNo())
            .amountVnd(payAmount)
            .userId(userDetails.getId())
            .clientIp(IpUtil.getRealIp(request))
            .createTime(order.createTime())
            .build();

        PaymentCreationResult result = vietQrAdapter.createDepositOrder(creationRequest);
        if (!result.isSuccess()) {
            return Map.of("code", 500, "msg", result.getErrorMessage());
        }

        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("code", 200);
        responseMap.put("outTradeNo", order.outTradeNo());
        responseMap.put("qrCodeData", result.getQrCodeData());
        responseMap.put("qrImageUrl", result.getQrImageUrl());
        return responseMap;
    }

    @ResponseBody
    @PostMapping(value = "vietqr/webhook", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, Object> vietqrWebhook(HttpServletRequest request, @RequestBody(required = false) String body) {
        Map<String, String> headers = extractHeaders(request);
        Map<String, String> params = singleValueParams(request);

        PaymentAdapter vietQrAdapter = paymentAdapterFactory.findAdapter(VIETQR_CHANNEL).orElse(null);
        if (!vietQrProperties.isConfigured() || vietQrAdapter == null) {
            return Map.of("code", 503, "msg", message("payment.vietqr.unavailable",
                "VietQR chưa được cấu hình an toàn"));
        }
        WebhookVerifyResult verifyResult = vietQrAdapter.verifyAndParseWebhook(headers, params, body);

        if (!verifyResult.isValid()) {
            metrics.recordPayment(PaymentProvider.VIETQR, PaymentOperation.WEBHOOK, Outcome.REJECTED);
            return Map.of("code", 401, "msg", verifyResult.getResponseMessage());
        }

        String outTradeNoStr = verifyResult.getOutTradeNo();
        if (outTradeNoStr == null || verifyResult.getBankTradeNo() == null
            || verifyResult.getAmountVnd() == null || !verifyResult.isSuccessful()) {
            metrics.recordPayment(PaymentProvider.VIETQR, PaymentOperation.WEBHOOK, Outcome.REJECTED);
            return Map.of("code", 400, "msg", "Missing outTradeNo");
        }

        try {
            long outTradeNo = Long.parseLong(outTradeNoStr);
            String bankTradeNo = verifyResult.getBankTradeNo();
            Integer amountVnd = verifyResult.getAmountVnd();

            PayOrderUpdateResult result = orderService.processPayOrder(outTradeNo, bankTradeNo, VIETQR_CHANNEL, amountVnd, true);
            metrics.recordPayment(PaymentProvider.VIETQR, PaymentOperation.WEBHOOK,
                paymentOutcome(result));
            return switch (result) {
                case SUCCESS -> Map.of("code", 200, "msg", "Confirm success");
                case ALREADY_PROCESSED -> Map.of("code", 200, "msg", "Order already processed");
                case NOT_FOUND, INVALID_CHANNEL -> Map.of("code", 404, "msg", "Order not found");
                case INVALID_AMOUNT -> Map.of("code", 400, "msg", "Invalid amount");
                case INVALID_ACCOUNT_AMOUNT -> Map.of("code", 500, "msg", "Invalid account amount");
            };
        } catch (Exception e) {
            metrics.recordPayment(PaymentProvider.VIETQR, PaymentOperation.WEBHOOK, Outcome.FAILED);
            log.error("Không thể xử lý VietQR Webhook", e);
            return Map.of("code", 500, "msg", "Error processing webhook");
        }
    }

    @ResponseBody
    @GetMapping("status/{outTradeNo}")
    public Map<String, Object> queryStatus(@PathVariable("outTradeNo") Long outTradeNo) {
        PayOrderState state = orderService.inspectPayOrder(outTradeNo, VNPAY_CHANNEL, 0);
        if (state == PayOrderState.INVALID_CHANNEL) {
            state = orderService.inspectPayOrder(outTradeNo, VIETQR_CHANNEL, 0);
        }
        return Map.of("outTradeNo", outTradeNo, "status", state.name());
    }

    @SneakyThrows
    @GetMapping("vnpay/return")
    public void vnpayReturn(HttpServletRequest request, HttpServletResponse response) {
        Map<String, String> params = singleValueParams(request);
        String paymentStatus = "vnpay-failed";
        if (vnpayService.verifySignature(params)) {
            try {
                long outTradeNo = Long.parseLong(params.get("vnp_TxnRef"));
                Integer amountVnd = parseAmountVnd(params.get("vnp_Amount"));
                if (amountVnd == null) {
                    throw new IllegalArgumentException("Số tiền VNPAY không hợp lệ");
                }
                PayOrderState orderState = orderService.inspectPayOrder(outTradeNo, VNPAY_CHANNEL, amountVnd);
                if ("00".equals(params.get("vnp_ResponseCode"))
                    && "00".equals(params.get("vnp_TransactionStatus"))) {
                    paymentStatus = switch (orderState) {
                        case SUCCESS -> "vnpay-success";
                        case PENDING -> "vnpay-processing";
                        default -> "vnpay-failed";
                    };
                }
            } catch (RuntimeException exception) {
                log.debug("Dữ liệu Return URL VNPAY không hợp lệ", exception);
            }
        }
        response.sendRedirect("/pay/index.html?payment=" + paymentStatus);
    }

    @ResponseBody
    @GetMapping(value = "vnpay/ipn", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> vnpayIpn(HttpServletRequest request) {
        Map<String, String> params = singleValueParams(request);
        if (!vnpayService.verifySignature(params)) {
            metrics.recordPayment(PaymentProvider.VNPAY, PaymentOperation.WEBHOOK, Outcome.REJECTED);
            return ipnResponse("97", "Invalid checksum");
        }

        try {
            Integer amountVnd = parseAmountVnd(params.get("vnp_Amount"));
            if (amountVnd == null) {
                metrics.recordPayment(PaymentProvider.VNPAY, PaymentOperation.WEBHOOK, Outcome.REJECTED);
                return ipnResponse("04", "Invalid amount");
            }
            long outTradeNo = Long.parseLong(params.get("vnp_TxnRef"));
            String tradeNo = params.get("vnp_TransactionNo");
            boolean successful = "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"));

            PayOrderUpdateResult result = orderService.processPayOrder(outTradeNo, tradeNo, VNPAY_CHANNEL,
                amountVnd, successful);
            metrics.recordPayment(PaymentProvider.VNPAY, PaymentOperation.WEBHOOK,
                paymentOutcome(result));
            return switch (result) {
                case SUCCESS -> ipnResponse("00", "Confirm success");
                case ALREADY_PROCESSED -> ipnResponse("02", "Order already confirmed");
                case NOT_FOUND, INVALID_CHANNEL -> ipnResponse("01", "Order not found");
                case INVALID_AMOUNT -> ipnResponse("04", "Invalid amount");
                case INVALID_ACCOUNT_AMOUNT -> ipnResponse("99", "Invalid account amount");
            };
        } catch (RuntimeException exception) {
            metrics.recordPayment(PaymentProvider.VNPAY, PaymentOperation.WEBHOOK, Outcome.FAILED);
            log.warn("Không thể xử lý IPN VNPAY", exception);
            return ipnResponse("99", "Unknown error");
        }
    }

    private Map<String, String> singleValueParams(HttpServletRequest request) {
        Map<String, String> params = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                params.put(key, values[0]);
            }
        });
        return params;
    }

    private Map<String, String> extractHeaders(HttpServletRequest request) {
        Map<String, String> headers = new HashMap<>();
        Enumeration<String> headerNames = request.getHeaderNames();
        if (headerNames != null) {
            while (headerNames.hasMoreElements()) {
                String name = headerNames.nextElement();
                headers.put(name.toLowerCase(), request.getHeader(name));
            }
        }
        return headers;
    }

    private Map<String, String> ipnResponse(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }

    private Outcome paymentOutcome(PayOrderUpdateResult result) {
        return switch (result) {
            case SUCCESS -> Outcome.SUCCESS;
            case ALREADY_PROCESSED -> Outcome.ALREADY_PROCESSED;
            case NOT_FOUND, INVALID_CHANNEL, INVALID_AMOUNT -> Outcome.REJECTED;
            case INVALID_ACCOUNT_AMOUNT -> Outcome.FAILED;
        };
    }

    private boolean acceptsJson(HttpServletRequest request) {
        String accept = request.getHeader(HttpHeaders.ACCEPT);
        return (accept != null && accept.contains(MediaType.APPLICATION_JSON_VALUE))
            || "XMLHttpRequest".equals(request.getHeader("X-Requested-With"));
    }

    private ResponseEntity<?> paymentError(HttpStatus status, String message, boolean jsonResponse) {
        if (jsonResponse) {
            return ResponseEntity.status(status).body(Map.of("code", status.value(), "msg", message));
        }
        return ResponseEntity.status(status).body(message);
    }

    private Integer parseAmountVnd(String rawAmountValue) {
        if (rawAmountValue == null) return null;
        try {
            long rawAmount = Long.parseLong(rawAmountValue);
            if (rawAmount <= 0) return null;
            if (rawAmount % 100 == 0 && rawAmount >= 100000) {
                return Math.toIntExact(rawAmount / 100);
            }
            return Math.toIntExact(rawAmount);
        } catch (RuntimeException exception) {
            return null;
        }
    }

    private String message(String key, String fallback) {
        String value = messages.get(key);
        return value == null || value.isBlank() ? fallback : value;
    }
}
