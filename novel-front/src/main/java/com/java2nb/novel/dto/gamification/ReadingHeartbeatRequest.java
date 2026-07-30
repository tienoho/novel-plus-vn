package com.java2nb.novel.dto.gamification;

import com.java2nb.novel.service.gamification.ReadingHeartbeatInput;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record ReadingHeartbeatRequest(
    @NotBlank
    @Pattern(regexp = "[A-Za-z0-9_-]{16,64}") String sessionId,
    @Min(1) long bookId,
    @Min(1) long bookIndexId,
    @Min(0) int sequence,
    @Min(0) @Max(600) int activeSeconds
) {
    public ReadingHeartbeatInput toInput() {
        return new ReadingHeartbeatInput(sessionId, bookId, bookIndexId, sequence, activeSeconds);
    }
}
