package com.example.ubi.config;

import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing")
public record BillingProperties(
        String checkoutSuccessUrl,
        String checkoutCancelUrl,
        /**
         * Comma-separated extra origins, e.g.
         * {@code https://ubi-telematics-dashboard.vercel.app,https://my-custom-domain.com}
         * Bound from {@code billing.allowed-return-origins} / {@code BILLING_ALLOWED_RETURN_ORIGINS}.
         */
        String allowedReturnOrigins
) {
    public BillingProperties {
        if (checkoutSuccessUrl == null || checkoutSuccessUrl.isBlank()) {
            checkoutSuccessUrl = "http://localhost:5173/?billing=success";
        }
        if (checkoutCancelUrl == null || checkoutCancelUrl.isBlank()) {
            checkoutCancelUrl = "http://localhost:5173/?billing=cancelled";
        }
        if (allowedReturnOrigins == null) {
            allowedReturnOrigins = "";
        }
    }

    public List<String> allowedReturnOriginList() {
        if (allowedReturnOrigins == null || allowedReturnOrigins.isBlank()) {
            return List.of();
        }
        return Arrays.stream(allowedReturnOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isBlank())
                .toList();
    }
}
