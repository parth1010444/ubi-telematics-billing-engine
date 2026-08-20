package com.example.ubi.application;

import com.example.ubi.config.StripeProperties;
import com.example.ubi.exception.StripeBillingException;
import com.stripe.exception.StripeException;
import com.stripe.model.billing.MeterEvent;
import com.stripe.net.RequestOptions;
import com.stripe.param.billing.MeterEventCreateParams;
import java.util.UUID;
import org.springframework.stereotype.Service;

@Service
public class StripeBillingService {

    private final StripeProperties stripeProperties;

    public StripeBillingService(StripeProperties stripeProperties) {
        this.stripeProperties = stripeProperties;
    }

    /**
     * Stripe Meter Events let the carrier report quantified UBI usage into Stripe Billing,
     * where the configured meter can aggregate usage charges onto the customer's invoice.
     *
     * Configure the secret key via `stripe.secret-key` or `STRIPE_SECRET_KEY`; using
     * RequestOptions keeps the service stateless instead of mutating the global Stripe.apiKey.
    */
    public MeterEvent reportUsage(String stripeCustomerId, int usageCharge) {
        return reportUsage(stripeCustomerId, usageCharge, "ubi-" + UUID.randomUUID());
    }

    /**
     * The identifier is deterministic for retryable billing records so the same telemetry event
     * cannot create multiple Stripe meter entries during retry or duplicate delivery scenarios.
     */
    public MeterEvent reportUsage(String stripeCustomerId, int usageCharge, String identifier) {
        MeterEventCreateParams params = MeterEventCreateParams.builder()
                .setEventName(stripeProperties.telematicsMeterEventName())
                .putPayload("stripe_customer_id", stripeCustomerId)
                .putPayload("value", String.valueOf(usageCharge))
                .setIdentifier(identifier)
                .build();

        RequestOptions requestOptions = RequestOptions.builder()
                .setApiKey(stripeProperties.secretKey())
                .build();

        try {
            return MeterEvent.create(params, requestOptions);
        } catch (StripeException exception) {
            throw new StripeBillingException("Failed to report UBI usage to Stripe", exception);
        }
    }
}
