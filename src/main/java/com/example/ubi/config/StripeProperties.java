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
        String telematicsMeterEventName
) {
}
