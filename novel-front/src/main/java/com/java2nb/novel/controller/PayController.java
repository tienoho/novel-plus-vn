package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.config.VnpayProperties;
import com.java2nb.novel.core.i18n.Messages;
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
import org.springframework.http.MediaType;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.LinkedHashMap;
import java.util.Map;

@Controller
@RequestMapping("pay")
@RequiredArgsConstructor
@Slf4j
public class PayController extends BaseController {

    private static final byte VNPAY_CHANNEL = 4;

    private final VnpayProperties vnpayProperties;
    private final VnpayService vnpayService;
    private final OrderService orderService;
    private final Messages messages;

    @SneakyThrows
    @PostMapping("vnpay")
    public void vnpay(Integer payAmount, HttpServletRequest request, HttpServletResponse response) {
        UserDetails userDetails = getUserDetails(request);
        if (userDetails == null) {
            response.sendRedirect("/user/login.html?originUrl=/pay/index.html");
            return;
        }
        if (!vnpayProperties.isConfigured()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, messages.get("payment.vnpay.unavailable"));
            return;
        }
        if (payAmount == null || !vnpayProperties.isAllowedAmount(payAmount)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST, messages.get("payment.amount.invalid"));
            return;
        }

        int accountAmount = vnpayProperties.calculateXu(payAmount);
        PayOrderCreation order = orderService.createPayOrder(VNPAY_CHANNEL, payAmount, accountAmount,
            userDetails.getId());
        response.sendRedirect(vnpayService.createPaymentUrl(order.outTradeNo(), payAmount,
            IpUtil.getRealIp(request), order.createTime()));
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
            return ipnResponse("97", "Invalid checksum");
        }

        try {
            Integer amountVnd = parseAmountVnd(params.get("vnp_Amount"));
            if (amountVnd == null) {
                return ipnResponse("04", "Invalid amount");
            }
            long outTradeNo = Long.parseLong(params.get("vnp_TxnRef"));
            String tradeNo = params.get("vnp_TransactionNo");
            boolean successful = "00".equals(params.get("vnp_ResponseCode"))
                && "00".equals(params.get("vnp_TransactionStatus"));

            PayOrderUpdateResult result = orderService.processPayOrder(outTradeNo, tradeNo, VNPAY_CHANNEL,
                amountVnd, successful);
            return switch (result) {
                case SUCCESS -> ipnResponse("00", "Confirm success");
                case ALREADY_PROCESSED -> ipnResponse("02", "Order already confirmed");
                case NOT_FOUND, INVALID_CHANNEL -> ipnResponse("01", "Order not found");
                case INVALID_AMOUNT -> ipnResponse("04", "Invalid amount");
                case INVALID_ACCOUNT_AMOUNT -> ipnResponse("99", "Invalid account amount");
            };
        } catch (RuntimeException exception) {
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

    private Map<String, String> ipnResponse(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }

    private Integer parseAmountVnd(String rawAmountValue) {
        try {
            long rawAmount = Long.parseLong(rawAmountValue);
            if (rawAmount <= 0 || rawAmount % 100 != 0) {
                return null;
            }
            return Math.toIntExact(rawAmount / 100);
        } catch (RuntimeException exception) {
            return null;
        }
    }
}
