package com.example.ubi.api;

import com.example.ubi.application.PolicyService;
import com.example.ubi.domain.model.Policy;
import com.example.ubi.dto.PolicyCreateRequest;
import com.example.ubi.dto.PolicyResponse;
import com.example.ubi.dto.PolicyStatusUpdateRequest;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @PostMapping
    public ResponseEntity<PolicyResponse> createPolicy(@Valid @RequestBody PolicyCreateRequest request) {
        Policy policy = policyService.createPolicy(request);

        return ResponseEntity
                .created(URI.create("/api/v1/policies/" + policy.getPolicyId()))
                .body(PolicyResponse.from(policy));
    }

    @GetMapping("/{policyId}")
    public PolicyResponse getPolicy(@PathVariable String policyId) {
        return PolicyResponse.from(policyService.getPolicy(policyId));
    }

    @PatchMapping("/{policyId}/status")
    public PolicyResponse updateStatus(
            @PathVariable String policyId,
            @Valid @RequestBody PolicyStatusUpdateRequest request
    ) {
        return PolicyResponse.from(policyService.updateStatus(policyId, request));
    }
}
