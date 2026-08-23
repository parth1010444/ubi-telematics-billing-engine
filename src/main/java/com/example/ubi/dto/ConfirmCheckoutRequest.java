package com.example.ubi.dto;

import jakarta.validation.constraints.NotBlank;

public class ConfirmCheckoutRequest {

    @NotBlank(message = "policyId is required")
    private String policyId;

    @NotBlank(message = "sessionId is required")
    private String sessionId;

    public String getPolicyId() {
        return policyId;
    }

    public void setPolicyId(String policyId) {
        this.policyId = policyId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }
}
