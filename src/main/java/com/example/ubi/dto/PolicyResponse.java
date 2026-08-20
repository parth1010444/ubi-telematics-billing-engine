package com.example.ubi.dto;

import com.example.ubi.domain.model.Policy;
import com.example.ubi.domain.model.PolicyStatus;
import java.math.BigDecimal;

public record PolicyResponse(
        String policyId,
        String userId,
        String stripeCustomerId,
        BigDecimal basePremium,
        PolicyStatus status
) {

    public static PolicyResponse from(Policy policy) {
        return new PolicyResponse(
                policy.getPolicyId(),
                policy.getUserId(),
                policy.getStripeCustomerId(),
                policy.getBasePremium(),
                policy.getStatus()
        );
    }
}
