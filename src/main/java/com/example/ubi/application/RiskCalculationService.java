package com.example.ubi.application;

import com.example.ubi.domain.model.TelemetryEvent;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class RiskCalculationService {

    private static final Logger LOGGER = LoggerFactory.getLogger(RiskCalculationService.class);

    private static final BigDecimal DISTANCE_RATE_PER_KM = new BigDecimal("0.5");
    private static final BigDecimal HARD_BRAKING_PENALTY = new BigDecimal("5");
    private static final int HIGH_SPEED_KMH = 100;
    private static final int ELEVATED_SPEED_KMH = 50;

    public record UsageChargeBreakdown(
            BigDecimal distanceCost,
            BigDecimal speedMultiplier,
            BigDecimal hardBrakingPenalty,
            BigDecimal total
    ) {
    }

    /**
     * Usage premium is {@code (D · C_base) × M_speed}, plus a flat $5 when the event
     * includes hard braking. The speed risk multiplier applies a surcharge for highway
     * and extreme speeding without changing the base policy premium.
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
        BigDecimal speedMultiplier = determineSpeedMultiplier(event.getSpeedKmh());
        BigDecimal distanceCost = distanceKm.multiply(DISTANCE_RATE_PER_KM).multiply(speedMultiplier);
        BigDecimal hardBrakingPenalty = event.isHardBraking() ? HARD_BRAKING_PENALTY : BigDecimal.ZERO;
        BigDecimal total = distanceCost.add(hardBrakingPenalty);
        String riskLevel = classifyRiskLevel(event);

        if (logResult) {
            LOGGER.info(
                    "UBI usage premium calculated: policyId={} eventId={} speedKmh={} distanceKm={} "
                            + "hardBraking={} riskLevel={} speedMultiplier={} distanceCost={} "
                            + "hardBrakingPenalty={} usageCharge={} "
                            + "(formula: (distanceKm * {}) * speedMultiplier + hardBrakingPenalty)",
                    event.getPolicyId(),
                    event.getEventId(),
                    event.getSpeedKmh(),
                    distanceKm,
                    event.isHardBraking(),
                    riskLevel,
                    speedMultiplier,
                    distanceCost,
                    hardBrakingPenalty,
                    total,
                    DISTANCE_RATE_PER_KM
            );
        }

        return new UsageChargeBreakdown(distanceCost, speedMultiplier, hardBrakingPenalty, total);
    }

    private BigDecimal determineSpeedMultiplier(int speedKmh) {
        if (speedKmh >= 100) {
            return new BigDecimal("1.50"); // 50% premium surcharge for extreme speeding
        } else if (speedKmh >= 50) {
            return new BigDecimal("1.20"); // 20% surcharge for highway speeding
        }
        return new BigDecimal("1.00"); // Standard safe driving rate
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
