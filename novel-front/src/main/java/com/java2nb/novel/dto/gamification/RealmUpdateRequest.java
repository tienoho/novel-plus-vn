package com.java2nb.novel.dto.gamification;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RealmUpdateRequest(
    @NotBlank @Size(max = 32) String realmType,
    @Min(0) long expectedVersion
) {
}
