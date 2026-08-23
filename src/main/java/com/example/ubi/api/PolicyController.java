package com.example.ubi.api;

import com.example.ubi.application.PolicyService;
import com.example.ubi.domain.model.Policy;
import com.example.ubi.dto.PolicyCreateRequest;
import com.example.ubi.dto.PolicyResponse;
import com.example.ubi.dto.PolicyStatusUpdateRequest;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping
    public List<PolicyResponse> listPolicies() {
        return policyService.listPolicyResponses();
    }

    @PostMapping
    public ResponseEntity<PolicyResponse> createPolicy(@Valid @RequestBody PolicyCreateRequest request) {
        PolicyResponse policy = policyService.createPolicyResponse(request);

        return ResponseEntity
                .created(URI.create("/api/v1/policies/" + policy.policyId()))
                .body(policy);
    }

    @GetMapping("/{policyId}")
    public PolicyResponse getPolicy(@PathVariable String policyId) {
        return policyService.getPolicyResponse(policyId);
    }

    @DeleteMapping("/{policyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deletePolicy(@PathVariable String policyId) {
        policyService.deletePolicy(policyId);
    }

    @PatchMapping("/{policyId}/status")
    public PolicyResponse updateStatus(
            @PathVariable String policyId,
            @Valid @RequestBody PolicyStatusUpdateRequest request
    ) {
        return policyService.updateStatusResponse(policyId, request);
    }
}
