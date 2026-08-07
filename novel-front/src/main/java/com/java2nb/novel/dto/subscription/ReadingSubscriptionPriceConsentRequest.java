package com.java2nb.novel.dto.subscription;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReadingSubscriptionPriceConsentRequest(
    @Min(0) long expectedVersion,
    @Min(1) long acceptedPlanVersion,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String clientRequestId) {
}
