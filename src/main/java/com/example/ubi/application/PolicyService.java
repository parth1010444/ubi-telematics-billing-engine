package com.example.ubi.application;

import com.example.ubi.domain.model.Policy;
import com.example.ubi.domain.repository.BillingUsageRecordRepository;
import com.example.ubi.domain.repository.PolicyRepository;
import com.example.ubi.domain.repository.TelemetryEventRepository;
import com.example.ubi.dto.PolicyCreateRequest;
import com.example.ubi.dto.PolicyResponse;
import com.example.ubi.dto.PolicyStatusUpdateRequest;
import com.example.ubi.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class PolicyService {

    private static final Logger LOGGER = LoggerFactory.getLogger(PolicyService.class);

    private final PolicyRepository policyRepository;
    private final TelemetryEventRepository telemetryEventRepository;
    private final BillingUsageRecordRepository billingUsageRecordRepository;
    private final RiskCalculationService riskCalculationService;

    public PolicyService(
            PolicyRepository policyRepository,
            TelemetryEventRepository telemetryEventRepository,
            BillingUsageRecordRepository billingUsageRecordRepository,
            RiskCalculationService riskCalculationService
    ) {
        this.policyRepository = policyRepository;
        this.telemetryEventRepository = telemetryEventRepository;
        this.billingUsageRecordRepository = billingUsageRecordRepository;
        this.riskCalculationService = riskCalculationService;
    }

    public List<PolicyResponse> listPolicyResponses() {
        return ensurePolicyNumbers(policyRepository.findAll()).stream()
                .sorted((left, right) -> Integer.compare(
                        left.getPolicyNumber() == null ? Integer.MAX_VALUE : left.getPolicyNumber(),
                        right.getPolicyNumber() == null ? Integer.MAX_VALUE : right.getPolicyNumber()
                ))
                .map(this::toResponse)
                .toList();
    }

    public List<Policy> listPolicies() {
        return policyRepository.findAll();
    }

    public Policy createPolicy(PolicyCreateRequest request) {
        Policy policy = new Policy();
        policy.setUserId(request.getUserId());
        policy.setStripeCustomerId(request.getStripeCustomerId());
        policy.setBasePremium(request.getBasePremium());
        policy.setStatus(request.getStatus());
        policy.setPaidAmountCents(0L);
        policy.setPolicyNumber(nextPolicyNumber());

        return policyRepository.save(policy);
    }

    public PolicyResponse createPolicyResponse(PolicyCreateRequest request) {
        return toResponse(createPolicy(request));
    }

    public Policy getPolicy(String policyId) {
        return policyRepository.findById(policyId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy not found"));
    }

    public PolicyResponse getPolicyResponse(String policyId) {
        Policy policy = ensurePolicyNumber(getPolicy(policyId));
        return toResponse(policy);
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

    public PolicyResponse updateStatusResponse(String policyId, PolicyStatusUpdateRequest request) {
        return toResponse(updateStatus(policyId, request));
    }

    /**
     * Hard-deletes a policy and its dependent telematics + billing outbox rows so demo data
     * can be reset without leaving orphaned Stripe retry noise.
     */
    public void deletePolicy(String policyId) {
        Policy policy = getPolicy(policyId);

        long telemetryDeleted = telemetryEventRepository.deleteByPolicyId(policyId);
        long billingDeleted = billingUsageRecordRepository.deleteByPolicyId(policyId);
        policyRepository.delete(policy);

        LOGGER.info(
                "Deleted policyId={} (telemetryEvents={}, billingUsageRecords={})",
                policyId,
                telemetryDeleted,
                billingDeleted
        );
    }

    public PolicyResponse recordCheckoutPayment(String policyId, String sessionId, long amountCents) {
        Policy policy = getPolicy(policyId);

        if (sessionId.equals(policy.getLastCheckoutSessionId())) {
            LOGGER.info("Checkout session {} already recorded for policyId={}", sessionId, policyId);
            return toResponse(policy);
        }

        long previousPaid = policy.getPaidAmountCents();
        policy.setPaidAmountCents(previousPaid + Math.max(0L, amountCents));
        policy.setLastCheckoutSessionId(sessionId);
        policy.setLastPaidAt(Instant.now());
        Policy saved = policyRepository.save(policy);

        LOGGER.info(
                "Recorded checkout payment: policyId={} sessionId={} amountCents={} paidTotalCents={}",
                policyId,
                sessionId,
                amountCents,
                saved.getPaidAmountCents()
        );

        return toResponse(saved);
    }

    public PolicyResponse toResponse(Policy policy) {
        AccrualSnapshot snapshot = accrualSnapshot(policy);
        return new PolicyResponse(
                policy.getPolicyId(),
                policy.displayName(),
                policy.getPolicyNumber(),
                policy.getUserId(),
                policy.getStripeCustomerId(),
                policy.getBasePremium(),
                policy.getStatus(),
                snapshot.accruedPremium(),
                snapshot.paidAmountCents(),
                snapshot.unpaidAmountCents(),
                snapshot.premiumPaymentStatus(),
                policy.getLastPaidAt()
        );
    }

    private int nextPolicyNumber() {
        return policyRepository.findAll().stream()
                .map(Policy::getPolicyNumber)
                .filter(number -> number != null && number > 0)
                .max(Integer::compareTo)
                .orElse(0) + 1;
    }

    private List<Policy> ensurePolicyNumbers(List<Policy> policies) {
        int next = policies.stream()
                .map(Policy::getPolicyNumber)
                .filter(number -> number != null && number > 0)
                .max(Integer::compareTo)
                .orElse(0);

        for (Policy policy : policies) {
            if (policy.getPolicyNumber() == null || policy.getPolicyNumber() <= 0) {
                next += 1;
                policy.setPolicyNumber(next);
                policyRepository.save(policy);
            }
        }
        return policies;
    }

    private Policy ensurePolicyNumber(Policy policy) {
        if (policy.getPolicyNumber() != null && policy.getPolicyNumber() > 0) {
            return policy;
        }
        policy.setPolicyNumber(nextPolicyNumber());
        return policyRepository.save(policy);
    }

    public AccrualSnapshot accrualSnapshot(Policy policy) {
        BigDecimal usageCharges = telemetryEventRepository
                .findByPolicyIdOrderByTimestampDesc(policy.getPolicyId())
                .stream()
                .map(riskCalculationService::calculateUsageCharge)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal accruedPremium = policy.getBasePremium().add(usageCharges);
        long accruedCents = toCents(accruedPremium);
        long paidCents = policy.getPaidAmountCents();
        long unpaidCents = Math.max(0L, accruedCents - paidCents);
        String paymentStatus = unpaidCents <= 0 && paidCents > 0 ? "PAID" : "NOT_PAID_YET";

        return new AccrualSnapshot(accruedPremium, accruedCents, paidCents, unpaidCents, paymentStatus);
    }

    public long toCents(BigDecimal dollars) {
        return dollars.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
    }

    public record AccrualSnapshot(
            BigDecimal accruedPremium,
            long accruedAmountCents,
            long paidAmountCents,
            long unpaidAmountCents,
            String premiumPaymentStatus
    ) {
    }
}
