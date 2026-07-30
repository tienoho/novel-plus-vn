package com.java2nb.novel.controller;

import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.config.ReaderEntitlementProperties;
import com.java2nb.novel.core.config.VietQrProperties;
import com.java2nb.novel.core.config.VnpayProperties;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.subscription.ReadingSubscriptionPeriodGrantResponse;
import com.java2nb.novel.dto.subscription.ReadingSubscriptionPlanResponse;
import com.java2nb.novel.dto.subscription.ReadingSubscriptionResponse;
import com.java2nb.novel.dto.subscription.ReadingSubscriptionCheckoutRequest;
import com.java2nb.novel.dto.subscription.ReadingSubscriptionCheckoutResponse;
import com.java2nb.novel.core.payment.PaymentAdapter;
import com.java2nb.novel.core.payment.PaymentAdapterFactory;
import com.java2nb.novel.core.payment.PaymentCreationRequest;
import com.java2nb.novel.core.payment.PaymentCreationResult;
import com.java2nb.novel.core.utils.IpUtil;
import com.java2nb.novel.common.annotation.LimitType;
import com.java2nb.novel.common.annotation.RateLimit;
import com.java2nb.novel.service.OrderService;
import com.java2nb.novel.service.ReadingSubscriptionCheckoutCreation;
import com.java2nb.novel.service.subscription.ReadingSubscriptionService;
import io.github.xxyopen.model.resp.RestResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("user/reading-subscriptions")
public class ReadingSubscriptionController extends BaseController {
    private final ReadingSubscriptionService service;
    private final ReaderEntitlementProperties properties;
    private final OrderService orderService;
    private final PaymentAdapterFactory paymentAdapterFactory;
    private final VnpayProperties vnpayProperties;
    private final VietQrProperties vietQrProperties;

    @GetMapping("plans")
    public RestResult<List<ReadingSubscriptionPlanResponse>> listPlans(HttpServletRequest request) {
        requireEnabled();
        requireUser(request);
        return RestResult.ok(service.listActivePlans().stream()
            .map(ReadingSubscriptionPlanResponse::from).toList());
    }

    @PostMapping("checkouts")
    @RateLimit(key = "reading_subscription_checkout", count = 5, timeWindowSeconds = 60,
        limitType = LimitType.USER)
    public RestResult<ReadingSubscriptionCheckoutResponse> createCheckout(
        @Valid @RequestBody ReadingSubscriptionCheckoutRequest input,
        HttpServletRequest request) {
        requireEnabled();
        long userId = requireUser(request).getId();
        byte channel = input.payChannel();
        PaymentAdapter adapter = paymentAdapterFactory.findAdapter(channel).orElse(null);
        if (adapter == null || (channel == 4 && !vnpayProperties.isConfigured())
            || (channel == 5 && !vietQrProperties.isConfigured())) {
            throw new BusinessException(ResponseStatus.READING_SUBSCRIPTION_CHECKOUT_UNAVAILABLE);
        }
        ReadingSubscriptionCheckoutCreation checkout;
        try {
            checkout = orderService.createSubscriptionCheckout(
                channel, userId, input.planCode(), input.clientRequestId());
        } catch (IllegalStateException exception) {
            throw new BusinessException(ResponseStatus.READING_SUBSCRIPTION_PURCHASE_CONFLICT);
        } catch (IllegalArgumentException exception) {
            throw new BusinessException(ResponseStatus.READING_SUBSCRIPTION_CHECKOUT_UNAVAILABLE);
        }
        PaymentCreationResult payment = adapter.createDepositOrder(PaymentCreationRequest.builder()
            .outTradeNo(checkout.outTradeNo()).amountVnd(checkout.amountVnd()).userId(userId)
            .clientIp(IpUtil.getRealIp(request)).createTime(checkout.createTime())
            .description("Thanh toán gói thuê bao Vé đọc").build());
        if (!payment.isSuccess()) {
            orderService.processPayOrder(checkout.outTradeNo(), "INIT_FAILED", channel,
                checkout.amountVnd(), false);
            throw new BusinessException(ResponseStatus.READING_SUBSCRIPTION_CHECKOUT_UNAVAILABLE);
        }
        return RestResult.ok(new ReadingSubscriptionCheckoutResponse(
            checkout.outTradeNo(), "PENDING", payment.getPaymentUrl(), payment.getQrCodeData(),
            payment.getQrImageUrl(), checkout.replay()));
    }

    @GetMapping("current")
    public RestResult<ReadingSubscriptionResponse> getCurrent(HttpServletRequest request) {
        requireEnabled();
        return RestResult.ok(ReadingSubscriptionResponse.from(
            service.getCurrentSubscription(requireUser(request).getId())));
    }

    @GetMapping("{subscriptionId}/period-grants")
    public RestResult<List<ReadingSubscriptionPeriodGrantResponse>> getPeriodGrants(
        @PathVariable("subscriptionId") @Min(1) long subscriptionId,
        @RequestParam(value = "limit", defaultValue = "50") @Min(1) @Max(100) int limit,
        HttpServletRequest request) {
        requireEnabled();
        long userId = requireUser(request).getId();
        return RestResult.ok(service.listPeriodGrants(userId, subscriptionId, limit).stream()
            .map(ReadingSubscriptionPeriodGrantResponse::from).toList());
    }

    private void requireEnabled() {
        if (!properties.isEnabled() || !properties.isConfigured()) {
            throw new BusinessException(ResponseStatus.READING_TICKET_DISABLED);
        }
    }

    private UserDetails requireUser(HttpServletRequest request) {
        UserDetails user = getUserDetails(request);
        if (user == null) {
            throw new BusinessException(ResponseStatus.NO_LOGIN);
        }
        return user;
    }
}
