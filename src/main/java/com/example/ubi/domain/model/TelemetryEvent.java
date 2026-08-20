package com.example.ubi.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "telemetry_events")
public class TelemetryEvent {

    @Id
    private String eventId;

    @Indexed
    private String policyId;

    private Instant timestamp;
    private int speedKmh;
    private boolean isHardBraking;
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

    public int getSpeedKmh() {
        return speedKmh;
    }

    public void setSpeedKmh(int speedKmh) {
        this.speedKmh = speedKmh;
    }

    public boolean isHardBraking() {
        return isHardBraking;
    }

    public void setHardBraking(boolean hardBraking) {
        isHardBraking = hardBraking;
    }

    public BigDecimal getDistanceTraveledKm() {
        return distanceTraveledKm;
    }

    public void setDistanceTraveledKm(BigDecimal distanceTraveledKm) {
        this.distanceTraveledKm = distanceTraveledKm;
    }
}
