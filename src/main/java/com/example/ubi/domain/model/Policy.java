package com.example.ubi.domain.model;

import java.math.BigDecimal;
import java.time.Instant;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

@Document(collection = "policies")
public class Policy {

    @Id
    private String policyId;

    private String userId;
    private String stripeCustomerId;
    private BigDecimal basePremium;
    private PolicyStatus status;

    /** Human-facing sequence used to build display names like Policy_1. */
    private Integer policyNumber;

    /** Cumulative amount successfully paid via Stripe Checkout, in cents. */
    private Long paidAmountCents;

    private String lastCheckoutSessionId;
    private Instant lastPaidAt;

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public BigDecimal getBasePremium() {
        return basePremium;
    }

    public void setBasePremium(BigDecimal basePremium) {
        this.basePremium = basePremium;
    }

    public PolicyStatus getStatus() {
        return status;
    }

    public void setStatus(PolicyStatus status) {
        this.status = status;
    }

    public Integer getPolicyNumber() {
        return policyNumber;
    }

    public void setPolicyNumber(Integer policyNumber) {
        this.policyNumber = policyNumber;
    }

    public String displayName() {
        return "Policy_" + (policyNumber == null ? "?" : policyNumber);
    }

    public Long getPaidAmountCents() {
        return paidAmountCents == null ? 0L : paidAmountCents;
    }

    public void setPaidAmountCents(Long paidAmountCents) {
        this.paidAmountCents = paidAmountCents == null ? 0L : paidAmountCents;
    }

    public String getLastCheckoutSessionId() {
        return lastCheckoutSessionId;
    }

    public void setLastCheckoutSessionId(String lastCheckoutSessionId) {
        this.lastCheckoutSessionId = lastCheckoutSessionId;
    }

    public Instant getLastPaidAt() {
        return lastPaidAt;
    }

    public void setLastPaidAt(Instant lastPaidAt) {
        this.lastPaidAt = lastPaidAt;
    }
}
