package com.java2nb.novel.core.payment;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PayoutResult {
    private boolean success;
    private String providerReference;
    private String errorCode;
    private String errorMessage;
}
