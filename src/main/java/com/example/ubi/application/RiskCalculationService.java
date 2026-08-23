package com.example.ubi.application;

import com.example.ubi.domain.model.TelemetryEvent;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RiskCalculationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RiskCalculationService.class);

    private static final BigDecimal HARD_BRAKING_PENALTY_UNITS = BigDecimal.valueOf(5);
    private static final BigDecimal DISTANCE_RATE_PER_KM = BigDecimal.valueOf(0.5);
    private static final int HIGH_SPEED_KMH = 120;
    private static final int ELEVATED_SPEED_KMH = 90;

    public record UsageChargeBreakdown(
            BigDecimal distanceCost,
            BigDecimal behaviorPenalty,
            BigDecimal total
    ) {
    }

    /**
     * Hard braking is treated as a near-term proxy for elevated driving risk in UBI pricing.
     * The distance component keeps the invoice metered, while the behavioral penalty nudges
     * the premium toward safer driving patterns without changing the base policy premium.
     * Stripe meters accept integer values, so callers convert this decimal risk score into
     * billable units at the Stripe boundary.
     */
    public BigDecimal calculateUsageCharge(TelemetryEvent event) {
        return calculateUsageChargeBreakdown(event, false).total();
    }

    public UsageChargeBreakdown calculateUsageChargeBreakdown(TelemetryEvent event) {
        return calculateUsageChargeBreakdown(event, true);
    }

    private UsageChargeBreakdown calculateUsageChargeBreakdown(TelemetryEvent event, boolean logResult) {
        BigDecimal distanceKm = event.getDistanceTraveledKm();
        BigDecimal distanceCost = distanceKm.multiply(DISTANCE_RATE_PER_KM);
        BigDecimal behaviorPenalty = event.isHardBraking() ? HARD_BRAKING_PENALTY_UNITS : BigDecimal.ZERO;
        BigDecimal total = distanceCost.add(behaviorPenalty);
        String riskLevel = classifyRiskLevel(event);

        if (logResult) {
            LOGGER.info(
                    "UBI usage premium calculated: policyId={} eventId={} speedKmh={} distanceKm={} "
                            + "hardBraking={} riskLevel={} distanceCost={} behaviorPenalty={} usageCharge={} "
                            + "(formula: distanceKm * {} + hardBrakePenalty {})",
                    event.getPolicyId(),
                    event.getEventId(),
                    event.getSpeedKmh(),
                    distanceKm,
                    event.isHardBraking(),
                    riskLevel,
                    distanceCost,
                    behaviorPenalty,
                    total,
                    DISTANCE_RATE_PER_KM,
                    HARD_BRAKING_PENALTY_UNITS
            );
        }

        return new UsageChargeBreakdown(distanceCost, behaviorPenalty, total);
    }

    public String classifyRiskLevel(TelemetryEvent event) {
        if (event.isHardBraking()) {
            return "HARD_BRAKE";
        }
        if (event.getSpeedKmh() >= HIGH_SPEED_KMH) {
            return "HIGH_SPEED";
        }
        if (event.getSpeedKmh() >= ELEVATED_SPEED_KMH) {
            return "ELEVATED";
        }
        return "NORMAL";
    }
}
