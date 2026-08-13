package com.java2nb.novel.dto.subscription;

import jakarta.validation.constraints.Min;

public record ReadingSubscriptionCancelRequest(@Min(0) long expectedVersion) {
}
