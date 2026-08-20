package com.example.ubi.dto;

import com.example.ubi.domain.model.PolicyStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public class PolicyCreateRequest {

    @NotBlank(message = "userId is required")
    private String userId;

    @NotBlank(message = "stripeCustomerId is required")
    private String stripeCustomerId;

    @NotNull(message = "basePremium is required")
    @DecimalMin(value = "0.0", inclusive = true, message = "basePremium cannot be negative")
    private BigDecimal basePremium;

    @NotNull(message = "status is required")
    private PolicyStatus status;

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
}
