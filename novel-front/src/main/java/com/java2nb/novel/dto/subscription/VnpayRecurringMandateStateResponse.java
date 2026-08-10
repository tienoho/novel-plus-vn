package com.java2nb.novel.dto.subscription;

import com.java2nb.novel.service.VnpayRecurringMandateState;

public record VnpayRecurringMandateStateResponse(boolean configured,
                                                 String status,
                                                 String clientRequestId,
                                                 String planCode,
                                                 Long acceptedPlanVersion) {
    public static VnpayRecurringMandateStateResponse from(VnpayRecurringMandateState value) {
        return new VnpayRecurringMandateStateResponse(value.configured(), value.status(),
            value.clientRequestId(), value.planCode(), value.acceptedPlanVersion());
    }
}
