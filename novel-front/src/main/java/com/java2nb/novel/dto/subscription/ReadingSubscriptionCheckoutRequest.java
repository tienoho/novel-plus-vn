package com.java2nb.novel.dto.subscription;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReadingSubscriptionCheckoutRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_]{3,32}") String planCode,
    @Min(4) @Max(5) byte payChannel,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String clientRequestId,
    boolean autoRenew,
    @Pattern(regexp = "VNPAY_RECURRING|WALLET_XU") String primaryFundingSource,
    @Pattern(regexp = "VNPAY_RECURRING|WALLET_XU") String fallbackFundingSource,
    @Min(1) long acceptedPlanVersion) {

    public ReadingSubscriptionCheckoutRequest(String planCode, byte payChannel,
                                              String clientRequestId) {
        this(planCode, payChannel, clientRequestId, false, null, null, 1);
    }
}
