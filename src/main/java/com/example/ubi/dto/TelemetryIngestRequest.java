package com.example.ubi.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.Instant;

public class TelemetryIngestRequest {

    @NotBlank(message = "eventId is required")
    private String eventId;

    @NotBlank(message = "policyId is required")
    private String policyId;

    @NotNull(message = "timestamp is required")
    @PastOrPresent(message = "timestamp cannot be in the future")
    private Instant timestamp;

    @NotNull(message = "speedKmh is required")
    @PositiveOrZero(message = "speedKmh cannot be negative")
    private Integer speedKmh;

    @NotNull(message = "isHardBraking is required")
    @JsonProperty("isHardBraking")
    private Boolean isHardBraking;

    @NotNull(message = "distanceTraveledKm is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "distanceTraveledKm cannot be negative")
    private BigDecimal distanceTraveledKm;

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    public Integer getSpeedKmh() {
        return speedKmh;
    }

    public void setSpeedKmh(Integer speedKmh) {
        this.speedKmh = speedKmh;
    }

    public Boolean isHardBraking() {
        return isHardBraking;
    }

    public void setHardBraking(Boolean hardBraking) {
        isHardBraking = hardBraking;
    }

    public void setIsHardBraking(Boolean hardBraking) {
        isHardBraking = hardBraking;
    }

    public BigDecimal getDistanceTraveledKm() {
        return distanceTraveledKm;
    }

    public void setDistanceTraveledKm(BigDecimal distanceTraveledKm) {
        this.distanceTraveledKm = distanceTraveledKm;
    }
}
