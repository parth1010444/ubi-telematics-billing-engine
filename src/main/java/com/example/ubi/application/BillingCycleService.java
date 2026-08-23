package com.example.ubi.application;

import com.example.ubi.domain.model.Policy;
import com.example.ubi.domain.model.PolicyStatus;
import com.example.ubi.dto.BillingCycleResponse;
import com.example.ubi.dto.PolicyResponse;
import com.example.ubi.exception.BillingPrerequisiteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BillingCycleService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BillingCycleService.class);

    private final PolicyService policyService;
    private final StripeBillingService stripeBillingService;

    public BillingCycleService(PolicyService policyService, StripeBillingService stripeBillingService) {
        this.policyService = policyService;
        this.stripeBillingService = stripeBillingService;
    }

    public BillingCycleResponse triggerCycle(String policyId, boolean force) {
        Policy policy = policyService.getPolicy(policyId);
        if (policy.getStatus() != PolicyStatus.ACTIVE) {
            throw new BillingPrerequisiteException(
                    "Only ACTIVE policies can be billed. Current status: " + policy.getStatus()
            );
        }

        PolicyService.AccrualSnapshot snapshot = policyService.accrualSnapshot(policy);

        LOGGER.info(
                "Triggering monthly billing: policyId={} stripeCustomerId={} accruedCents={} "
                        + "paidCents={} unpaidCents={} force={}",
                policyId,
                policy.getStripeCustomerId(),
                snapshot.accruedAmountCents(),
                snapshot.paidAmountCents(),
                snapshot.unpaidAmountCents(),
                force
        );

        if (snapshot.unpaidAmountCents() <= 0) {
            if (snapshot.paidAmountCents() > 0 && !force) {
                return new BillingCycleResponse(
                        null,
                        "already_paid",
                        null,
                        null,
                        0L,
                        snapshot.accruedAmountCents(),
                        snapshot.paidAmountCents(),
                        0L
                );
            }
            return new BillingCycleResponse(
                    "no_charge",
                    "zero_balance",
                    null,
                    null,
                    0L,
                    snapshot.accruedAmountCents(),
                    snapshot.paidAmountCents(),
                    0L
            );
        }

        BillingCycleResponse checkout = stripeBillingService.createCheckoutSession(
                policy.getStripeCustomerId(),
                snapshot.unpaidAmountCents(),
                policyId
        );

        return new BillingCycleResponse(
                checkout.invoiceId(),
                checkout.status(),
                checkout.hostedInvoiceUrl(),
                checkout.invoicePdf(),
                checkout.amountCents(),
                snapshot.accruedAmountCents(),
                snapshot.paidAmountCents(),
                snapshot.unpaidAmountCents()
        );
    }

    public PolicyResponse confirmCheckout(String policyId, String sessionId) {
        long amountCents = stripeBillingService.verifyPaidCheckoutSession(sessionId, policyId);
        return policyService.recordCheckoutPayment(policyId, sessionId, amountCents);
    }
}
