package com.example.ubi.application;

import com.example.ubi.domain.model.TelemetryEvent;
import java.math.BigDecimal;
import org.springframework.stereotype.Service;

@Service
public class RiskCalculationService {

    private static final BigDecimal HARD_BRAKING_PENALTY_UNITS = BigDecimal.valueOf(5);
    private static final BigDecimal DISTANCE_RATE_PER_KM = BigDecimal.valueOf(0.5);

    /**
     * Hard braking is treated as a near-term proxy for elevated driving risk in UBI pricing.
     * The distance component keeps the invoice metered, while the behavioral penalty nudges
     * the premium toward safer driving patterns without changing the base policy premium.
     * Stripe meters accept integer values, so callers convert this decimal risk score into
     * billable units at the Stripe boundary.
     */
    public BigDecimal calculateUsageCharge(TelemetryEvent event) {
        BigDecimal distanceCost = event.getDistanceTraveledKm().multiply(DISTANCE_RATE_PER_KM);
        BigDecimal behaviorPenalty = event.isHardBraking() ? HARD_BRAKING_PENALTY_UNITS : BigDecimal.ZERO;

        return distanceCost.add(behaviorPenalty);
    }
}
