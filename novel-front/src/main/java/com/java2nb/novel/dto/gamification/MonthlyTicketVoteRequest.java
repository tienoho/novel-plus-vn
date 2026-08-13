package com.java2nb.novel.dto.gamification;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MonthlyTicketVoteRequest(
    @Min(1) long seasonId,
    @Min(1) @Max(1_000_000) int amount,
    @NotBlank @Size(max = 64) String clientRequestId
) {
}
