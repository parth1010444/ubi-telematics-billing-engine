package com.example.ubi.dto;

import com.example.ubi.domain.model.PolicyStatus;
import jakarta.validation.constraints.NotNull;

public class PolicyStatusUpdateRequest {

    @NotNull(message = "status is required")
    private PolicyStatus status;

    public PolicyStatus getStatus() {
        return status;
    }

    public void setStatus(PolicyStatus status) {
        this.status = status;
    }
}
