package com.example.ubi.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "billing")
public record BillingProperties(
        String checkoutSuccessUrl,
        String checkoutCancelUrl,
        List<String> allowedReturnOrigins
) {
    public BillingProperties {
        if (checkoutSuccessUrl == null || checkoutSuccessUrl.isBlank()) {
            checkoutSuccessUrl = "http://localhost:5173/?billing=success";
        }
        if (checkoutCancelUrl == null || checkoutCancelUrl.isBlank()) {
            checkoutCancelUrl = "http://localhost:5173/?billing=cancelled";
        }
        allowedReturnOrigins = allowedReturnOrigins == null
                ? List.of()
                : allowedReturnOrigins.stream()
                        .filter(origin -> origin != null && !origin.isBlank())
                        .map(String::trim)
                        .toList();
    }
}
