package com.example.ubi.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "stripe")
public record StripeProperties(
        @NotBlank(message = "stripe.secret-key is required")
        String secretKey,
        @NotBlank(message = "stripe.telematics-meter-event-name is required")
        String telematicsMeterEventName,
        String publishableKey
) {
    public StripeProperties {
        if (publishableKey == null) {
            publishableKey = "";
        }
        if (secretKey != null && secretKey.startsWith("pk_")) {
            throw new IllegalArgumentException(
                    "stripe.secret-key is a publishable key (pk_...). "
                            + "Use the Secret key (sk_test_...) from https://dashboard.stripe.com/test/apikeys. "
                            + "Note: shell env STRIPE_SECRET_KEY overrides application-local.yml — "
                            + "run `unset STRIPE_SECRET_KEY` if you exported the publishable key by mistake."
            );
        }
    }
}
