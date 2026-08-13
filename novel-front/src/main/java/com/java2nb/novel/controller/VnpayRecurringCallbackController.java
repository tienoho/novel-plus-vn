package com.java2nb.novel.controller;

import com.java2nb.novel.core.config.VnpayRecurringProperties;
import com.java2nb.novel.core.observability.NovelBusinessMetrics;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.Outcome;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.PaymentOperation;
import com.java2nb.novel.core.observability.NovelBusinessMetrics.PaymentProvider;
import com.java2nb.novel.service.VnpayRecurringMandateService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("payment/vnpay-recurring")
public class VnpayRecurringCallbackController {

    private final VnpayRecurringMandateService mandateService;
    private final VnpayRecurringProperties properties;
    private final NovelBusinessMetrics metrics;

    @GetMapping(value = "ipn", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> ipn(HttpServletRequest request) {
        VnpayRecurringMandateService.CallbackResult result = mandateService.processIpn(parameters(request));
        metrics.recordPayment(PaymentProvider.VNPAY_RECURRING, PaymentOperation.WEBHOOK,
            switch (result) {
                case SUCCESS -> Outcome.SUCCESS;
                case ALREADY_PROCESSED -> Outcome.ALREADY_PROCESSED;
                case NOT_FOUND, INVALID_REQUEST, INVALID_CHECKSUM -> Outcome.REJECTED;
                case SYSTEM_ERROR -> Outcome.FAILED;
            });
        return switch (result) {
            case SUCCESS -> response("00", "Confirm Success");
            case ALREADY_PROCESSED -> response("02", "Request already processed");
            case NOT_FOUND, INVALID_REQUEST -> response("01", "Order not found");
            case INVALID_CHECKSUM -> response("97", "Invalid checksum");
            case SYSTEM_ERROR -> response("99", "Unknown error");
        };
    }

    @GetMapping("return")
    public void paymentReturn(HttpServletRequest request, HttpServletResponse response) throws IOException {
        redirect(response, mandateService.inspectReturn(parameters(request)));
    }

    @GetMapping("cancel")
    public void cancel(HttpServletResponse response) throws IOException {
        redirect(response, "cancelled");
    }

    private void redirect(HttpServletResponse response, String status) throws IOException {
        String target = properties.getResultRedirectUrl();
        response.sendRedirect(target + (target.contains("?") ? "&" : "?") + "mandate=" + status);
    }

    private Map<String, String> parameters(HttpServletRequest request) {
        Map<String, String> result = new LinkedHashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                result.put(key, values[0]);
            }
        });
        return result;
    }

    private Map<String, String> response(String code, String message) {
        return Map.of("RspCode", code, "Message", message);
    }
}
