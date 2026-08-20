package com.example.ubi.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "billing_usage_records")
@CompoundIndex(name = "status_next_retry_idx", def = "{'status': 1, 'nextRetryAt': 1}")
public class BillingUsageRecord {

    @Id
    private String billingUsageId;

    @Indexed(unique = true)
    private String telemetryEventId;

    @Indexed
    private String policyId;

    private String stripeCustomerId;
    private BigDecimal usageCharge;
    private int billableUsageUnits;
    private BillingUsageStatus status;
    private String stripeMeterEventIdentifier;
    private int attemptCount;
    private String lastError;
    private Instant nextRetryAt;
    private Instant createdAt;
    private Instant updatedAt;

    public String getBillingUsageId() {
        return billingUsageId;
    }

    public void setBillingUsageId(String billingUsageId) {
        this.billingUsageId = billingUsageId;
    }

    public String getTelemetryEventId() {
        return telemetryEventId;
    }

    public void setTelemetryEventId(String telemetryEventId) {
        this.telemetryEventId = telemetryEventId;
    }

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public BigDecimal getUsageCharge() {
        return usageCharge;
    }

    public void setUsageCharge(BigDecimal usageCharge) {
        this.usageCharge = usageCharge;
    }

    public int getBillableUsageUnits() {
        return billableUsageUnits;
    }

    public void setBillableUsageUnits(int billableUsageUnits) {
        this.billableUsageUnits = billableUsageUnits;
    }

    public BillingUsageStatus getStatus() {
        return status;
    }

    public void setStatus(BillingUsageStatus status) {
        this.status = status;
    }

    public String getStripeMeterEventIdentifier() {
        return stripeMeterEventIdentifier;
    }

    public void setStripeMeterEventIdentifier(String stripeMeterEventIdentifier) {
        this.stripeMeterEventIdentifier = stripeMeterEventIdentifier;
    }

    public int getAttemptCount() {
        return attemptCount;
    }

    public void setAttemptCount(int attemptCount) {
        this.attemptCount = attemptCount;
    }

    public String getLastError() {
        return lastError;
    }

    public void setLastError(String lastError) {
        this.lastError = lastError;
    }

    public Instant getNextRetryAt() {
        return nextRetryAt;
    }

    public void setNextRetryAt(Instant nextRetryAt) {
        this.nextRetryAt = nextRetryAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }
}
