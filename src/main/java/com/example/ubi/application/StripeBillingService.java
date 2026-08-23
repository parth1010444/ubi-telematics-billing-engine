package com.example.ubi.application;

import com.example.ubi.config.CheckoutReturnUrlResolver;
import com.example.ubi.config.StripeProperties;
import com.example.ubi.dto.BillingCycleResponse;
import com.example.ubi.exception.BillingPrerequisiteException;
import com.example.ubi.exception.StripeBillingException;
import com.stripe.exception.StripeException;
import com.stripe.model.billing.MeterEvent;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.param.billing.MeterEventCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class StripeBillingService {

    private static final Logger LOGGER = LoggerFactory.getLogger(StripeBillingService.class);

    private final StripeProperties stripeProperties;
    private final CheckoutReturnUrlResolver checkoutReturnUrlResolver;

    public StripeBillingService(
            StripeProperties stripeProperties,
            CheckoutReturnUrlResolver checkoutReturnUrlResolver
    ) {
        this.stripeProperties = stripeProperties;
        this.checkoutReturnUrlResolver = checkoutReturnUrlResolver;
    }

    /**
     * Stripe Meter Events let the carrier report quantified UBI usage into Stripe Billing,
     * where the configured meter can aggregate usage charges onto the customer's invoice.
     */
    public MeterEvent reportUsage(String stripeCustomerId, int usageCharge) {
        return reportUsage(stripeCustomerId, usageCharge, "ubi-" + UUID.randomUUID());
    }

    public MeterEvent reportUsage(String stripeCustomerId, int usageCharge, String identifier) {
        MeterEventCreateParams params = MeterEventCreateParams.builder()
                .setEventName(stripeProperties.telematicsMeterEventName())
                .putPayload("stripe_customer_id", stripeCustomerId)
                .putPayload("value", String.valueOf(usageCharge))
                .setIdentifier(identifier)
                .build();

        try {
            return MeterEvent.create(params, requestOptions());
        } catch (StripeException exception) {
            throw new StripeBillingException(
                    "Failed to report UBI usage to Stripe: " + exception.getMessage(),
                    exception
            );
        }
    }

    /**
     * Creates a Stripe Checkout Session so the customer pays the unpaid premium themselves
     * on Stripe-hosted checkout (no server-side auto-charge).
     */
    public BillingCycleResponse createCheckoutSession(
            String stripeCustomerId,
            long amountCents,
            String policyId,
            String returnOrigin
    ) {
        if (amountCents <= 0) {
            LOGGER.info("Skipping Stripe Checkout for policyId={} — unpaid amount is zero", policyId);
            return new BillingCycleResponse("no_charge", "zero_balance", null, null, 0L, 0L, 0L, 0L);
        }

        String successUrl = checkoutReturnUrlResolver.successUrl(returnOrigin, policyId);
        String cancelUrl = checkoutReturnUrlResolver.cancelUrl(returnOrigin, policyId);

        SessionCreateParams params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomer(stripeCustomerId)
                .setSuccessUrl(successUrl)
                .setCancelUrl(cancelUrl)
                .setClientReferenceId(policyId)
                .putMetadata("policyId", policyId)
                .addLineItem(
                        SessionCreateParams.LineItem.builder()
                                .setQuantity(1L)
                                .setPriceData(
                                        SessionCreateParams.LineItem.PriceData.builder()
                                                .setCurrency("usd")
                                                .setUnitAmount(amountCents)
                                                .setProductData(
                                                        SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                                                .setName("UBI unpaid monthly premium")
                                                                .setDescription(
                                                                        "Unpaid usage-based insurance premium for policy "
                                                                                + policyId
                                                                )
                                                                .build()
                                                )
                                                .build()
                                )
                                .build()
                )
                .build();

        try {
            Session session = Session.create(params, requestOptions());
            LOGGER.info(
                    "Created Stripe Checkout Session: policyId={} sessionId={} amountCents={} url={} successUrl={}",
                    policyId,
                    session.getId(),
                    amountCents,
                    session.getUrl(),
                    successUrl
            );
            return new BillingCycleResponse(
                    session.getId(),
                    "checkout_pending",
                    session.getUrl(),
                    null,
                    amountCents,
                    0L,
                    0L,
                    amountCents
            );
        } catch (StripeException exception) {
            throw new StripeBillingException(
                    "Failed to create Stripe Checkout Session: " + exception.getMessage(),
                    exception
            );
        }
    }

    public long verifyPaidCheckoutSession(String sessionId, String policyId) {
        try {
            Session session = Session.retrieve(sessionId, requestOptions());
            String sessionPolicyId = session.getClientReferenceId();
            if (sessionPolicyId == null && session.getMetadata() != null) {
                sessionPolicyId = session.getMetadata().get("policyId");
            }
            if (sessionPolicyId != null && !sessionPolicyId.equals(policyId)) {
                throw new BillingPrerequisiteException(
                        "Checkout session does not belong to policy " + policyId
                );
            }
            if (!"paid".equalsIgnoreCase(session.getPaymentStatus())
                    && !"complete".equalsIgnoreCase(session.getStatus())) {
                throw new BillingPrerequisiteException(
                        "Checkout session is not paid yet (status="
                                + session.getStatus()
                                + ", payment_status="
                                + session.getPaymentStatus()
                                + ")"
                );
            }
            Long amountTotal = session.getAmountTotal();
            if (amountTotal == null || amountTotal <= 0) {
                throw new BillingPrerequisiteException("Checkout session has no payable amount");
            }
            LOGGER.info(
                    "Verified paid Checkout Session: sessionId={} policyId={} amountCents={}",
                    sessionId,
                    policyId,
                    amountTotal
            );
            return amountTotal;
        } catch (BillingPrerequisiteException exception) {
            throw exception;
        } catch (StripeException exception) {
            throw new StripeBillingException(
                    "Failed to verify Stripe Checkout Session: " + exception.getMessage(),
                    exception
            );
        }
    }

    private RequestOptions requestOptions() {
        return RequestOptions.builder()
                .setApiKey(stripeProperties.secretKey())
                .build();
    }
}
