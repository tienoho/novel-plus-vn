package com.java2nb.novel.dto.subscription;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

public record ReadingSubscriptionRenewalSettingsRequest(
    @Min(0) long expectedVersion,
    boolean autoRenew,
    @Pattern(regexp = "VNPAY_RECURRING|WALLET_XU") String primaryFundingSource,
    @Pattern(regexp = "VNPAY_RECURRING|WALLET_XU") String fallbackFundingSource,
    @Min(1) long acceptedPlanVersion) {
}
