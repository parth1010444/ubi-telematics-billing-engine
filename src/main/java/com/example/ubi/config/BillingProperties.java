package com.example.ubi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing")
public record BillingProperties(
        String checkoutSuccessUrl,
        String checkoutCancelUrl
) {
    public BillingProperties {
        if (checkoutSuccessUrl == null || checkoutSuccessUrl.isBlank()) {
            checkoutSuccessUrl = "http://localhost:5173/?billing=success";
        }
        if (checkoutCancelUrl == null || checkoutCancelUrl.isBlank()) {
            checkoutCancelUrl = "http://localhost:5173/?billing=cancelled";
        }
    }
}
