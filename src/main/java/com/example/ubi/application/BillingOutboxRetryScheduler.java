package com.example.ubi.application;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class BillingOutboxRetryScheduler {

    private final BillingOutboxService billingOutboxService;

    public BillingOutboxRetryScheduler(BillingOutboxService billingOutboxService) {
        this.billingOutboxService = billingOutboxService;
    }

    @Scheduled(fixedDelayString = "${billing.retry.fixed-delay-ms:60000}")
    public void retryDueStripeUsageReports() {
        billingOutboxService.retryDueRecords();
    }
}
