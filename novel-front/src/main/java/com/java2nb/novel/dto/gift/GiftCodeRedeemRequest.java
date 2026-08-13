package com.java2nb.novel.dto.gift;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record GiftCodeRedeemRequest(
    @NotBlank @Size(min = 16, max = 80) String code,
    @NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{8,64}") String clientRequestId) {
}
