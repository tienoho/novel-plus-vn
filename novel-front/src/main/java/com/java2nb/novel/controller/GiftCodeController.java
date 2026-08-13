package com.java2nb.novel.controller;

import com.java2nb.novel.common.annotation.LimitType;
import com.java2nb.novel.common.annotation.RateLimit;
import com.java2nb.novel.core.bean.UserDetails;
import com.java2nb.novel.core.enums.ResponseStatus;
import com.java2nb.novel.core.exception.BusinessException;
import com.java2nb.novel.dto.gift.GiftCodeRedeemRequest;
import com.java2nb.novel.dto.gift.GiftCodeRedeemResponse;
import com.java2nb.novel.dto.gift.GiftRedemptionHistoryPageResponse;
import com.java2nb.novel.service.gift.GiftCodeProperties;
import com.java2nb.novel.service.gift.GiftCodeRedeemException;
import com.java2nb.novel.service.gift.GiftCodeService;
import com.java2nb.novel.service.gift.GiftRedeemCommand;
import io.github.xxyopen.model.resp.RestResult;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.util.Date;

@RestController
public class GiftCodeController extends BaseController {
    private final GiftCodeService service;
    private final GiftCodeProperties properties;
    private final Clock clock;

    @Autowired
    public GiftCodeController(GiftCodeService service, GiftCodeProperties properties) {
        this(service, properties, Clock.systemUTC());
    }

    GiftCodeController(GiftCodeService service, GiftCodeProperties properties, Clock clock) {
        this.service = service;
        this.properties = properties;
        this.clock = clock;
    }

    @PostMapping("user/gift-codes/redeem")
    @RateLimit(key = "gift_code_redeem", count = 10, timeWindowSeconds = 60,
        limitType = LimitType.USER)
    public RestResult<GiftCodeRedeemResponse> redeem(
        @Valid @RequestBody GiftCodeRedeemRequest input, HttpServletRequest request) {
        requireReady();
        try {
            return RestResult.ok(GiftCodeRedeemResponse.from(service.redeem(
                new GiftRedeemCommand(requireUser(request).getId(), input.code(),
                    input.clientRequestId(), Date.from(clock.instant())))));
        } catch (GiftCodeRedeemException exception) {
            throw new BusinessException(ResponseStatus.GIFT_CODE_REJECTED);
        }
    }

    @GetMapping("user/gift-codes/redemptions")
    public RestResult<GiftRedemptionHistoryPageResponse> listRedemptions(
        @RequestParam(value = "page", defaultValue = "1") @Min(1) int page,
        @RequestParam(value = "limit", defaultValue = "20") @Min(1) @Max(100) int limit,
        HttpServletRequest request) {
        long userId = requireUser(request).getId();
        return RestResult.ok(GiftRedemptionHistoryPageResponse.from(
            service.listUserRedemptions(userId, page, limit)));
    }

    private void requireReady() {
        if (!properties.isReady()) {
            throw new BusinessException(ResponseStatus.GIFT_CODE_DISABLED);
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
