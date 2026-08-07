package com.java2nb.novel.dto.subscription;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record VnpayRecurringMandateRequest(
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{2,32}") String planCode,
    @Min(1) long acceptedPlanVersion) {
}
