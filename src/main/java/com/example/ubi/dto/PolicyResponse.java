package com.example.ubi.dto;

import com.example.ubi.domain.model.PolicyStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record PolicyResponse(
        String policyId,
        String name,
        Integer policyNumber,
        String userId,
        String stripeCustomerId,
        BigDecimal basePremium,
        PolicyStatus status,
        BigDecimal accruedPremium,
        long paidAmountCents,
        long unpaidAmountCents,
        String premiumPaymentStatus,
        Instant lastPaidAt
) {
}
