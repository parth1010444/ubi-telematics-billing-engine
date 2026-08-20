package com.example.ubi.application;

import com.example.ubi.domain.model.Policy;
import com.example.ubi.domain.repository.PolicyRepository;
import com.example.ubi.dto.PolicyCreateRequest;
import com.example.ubi.dto.PolicyStatusUpdateRequest;
import com.example.ubi.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class PolicyService {

    private final PolicyRepository policyRepository;

    public PolicyService(PolicyRepository policyRepository) {
        this.policyRepository = policyRepository;
    }

    public Policy createPolicy(PolicyCreateRequest request) {
        Policy policy = new Policy();
        policy.setUserId(request.getUserId());
        policy.setStripeCustomerId(request.getStripeCustomerId());
        policy.setBasePremium(request.getBasePremium());
        policy.setStatus(request.getStatus());

        return policyRepository.save(policy);
    }

    public Policy getPolicy(String policyId) {
        return policyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found"));
    }

    /**
     * Policy status gates telematics billing: suspended or cancelled policies should not accrue
     * usage charges, which protects customers and keeps carrier billing aligned with coverage.
     */
    public Policy updateStatus(String policyId, PolicyStatusUpdateRequest request) {
        Policy policy = getPolicy(policyId);
        policy.setStatus(request.getStatus());
        return policyRepository.save(policy);
    }
}
