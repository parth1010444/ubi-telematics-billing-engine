package com.example.ubi.dto;

import com.example.ubi.domain.model.TelemetryEvent;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.math.BigDecimal;
import java.time.Instant;

public record TelemetryHistoryItemResponse(
        String eventId,
        String policyId,
        Instant timestamp,
        int speedKmh,
        @JsonProperty("isHardBraking") boolean isHardBraking,
        BigDecimal distanceTraveledKm,
        BigDecimal surchargeAdded,
        String riskLevel
) {
    public static TelemetryHistoryItemResponse from(
            TelemetryEvent event,
            BigDecimal surchargeAdded,
            String riskLevel
    ) {
        return new TelemetryHistoryItemResponse(
                event.getEventId(),
                event.getPolicyId(),
                event.getTimestamp(),
                event.getSpeedKmh(),
                event.isHardBraking(),
                event.getDistanceTraveledKm(),
                surchargeAdded,
                riskLevel
        );
    }
}
