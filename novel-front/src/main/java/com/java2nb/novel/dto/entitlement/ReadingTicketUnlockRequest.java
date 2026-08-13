package com.java2nb.novel.dto.entitlement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReadingTicketUnlockRequest(
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{8,64}")
    String clientRequestId
) {
}
