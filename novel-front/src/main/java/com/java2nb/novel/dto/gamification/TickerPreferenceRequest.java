package com.java2nb.novel.dto.gamification;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TickerPreferenceRequest(
    @NotNull Boolean optOut,
    @Min(0) long expectedVersion
) {
}
