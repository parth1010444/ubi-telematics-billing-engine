package com.example.ubi.application;

import com.example.ubi.domain.model.BillingUsageRecord;
import com.example.ubi.domain.model.BillingUsageStatus;
import com.example.ubi.domain.model.Policy;
import com.example.ubi.domain.model.TelemetryEvent;
import com.example.ubi.domain.repository.BillingUsageRecordRepository;
import com.stripe.model.billing.MeterEvent;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class BillingOutboxService {

    private static final Logger LOGGER = LoggerFactory.getLogger(BillingOutboxService.class);
    private static final Duration RETRY_BACKOFF = Duration.ofMinutes(5);

    private final BillingUsageRecordRepository billingUsageRecordRepository;
    private final StripeBillingService stripeBillingService;

    public BillingOutboxService(
            BillingUsageRecordRepository billingUsageRecordRepository,
            StripeBillingService stripeBillingService
    ) {
        this.billingUsageRecordRepository = billingUsageRecordRepository;
        this.stripeBillingService = stripeBillingService;
    }

    /**
     * The billing usage record is the audit boundary between actuarial scoring and invoicing.
     * Persisting it before Stripe is called lets operations reconcile every charged kilometer
     * and retry payment-meter reporting without replaying the raw telematics payload.
     */
    public BillingUsageRecord createPendingRecord(
            Policy policy,
            TelemetryEvent event,
            BigDecimal usageCharge,
            int billableUsageUnits
    ) {
        Instant now = Instant.now();

        BillingUsageRecord record = new BillingUsageRecord();
        record.setTelemetryEventId(event.getEventId());
        record.setPolicyId(event.getPolicyId());
        record.setStripeCustomerId(policy.getStripeCustomerId());
        record.setUsageCharge(usageCharge);
        record.setBillableUsageUnits(billableUsageUnits);
        record.setAttemptCount(0);
        record.setCreatedAt(now);
        record.setUpdatedAt(now);

        if (billableUsageUnits > 0) {
            record.setStatus(BillingUsageStatus.PENDING);
            record.setNextRetryAt(now);
        } else {
            record.setStatus(BillingUsageStatus.REPORTED);
            record.setStripeMeterEventIdentifier("zero-usage-no-stripe-event");
        }

        return billingUsageRecordRepository.save(record);
    }

    public void reportNow(BillingUsageRecord record) {
        if (record.getStatus() == BillingUsageStatus.REPORTED || record.getBillableUsageUnits() <= 0) {
            return;
        }

        String identifier = stripeIdentifier(record);
        try {
            MeterEvent ignored = stripeBillingService.reportUsage(
                    record.getStripeCustomerId(),
                    record.getBillableUsageUnits(),
                    identifier
            );

            record.setStatus(BillingUsageStatus.REPORTED);
            record.setStripeMeterEventIdentifier(identifier);
            record.setLastError(null);
            record.setNextRetryAt(null);
        } catch (RuntimeException exception) {
            record.setStatus(BillingUsageStatus.FAILED);
            record.setLastError(truncate(exception.getMessage()));
            record.setNextRetryAt(Instant.now().plus(RETRY_BACKOFF));
            LOGGER.warn("Stripe usage reporting failed for telemetryEventId={}", record.getTelemetryEventId(), exception);
        } finally {
            record.setAttemptCount(record.getAttemptCount() + 1);
            record.setUpdatedAt(Instant.now());
            billingUsageRecordRepository.save(record);
        }
    }

    public void retryDueRecords() {
        List<BillingUsageRecord> records = billingUsageRecordRepository
                .findTop50ByStatusInAndNextRetryAtLessThanEqualOrderByCreatedAtAsc(
                        List.of(BillingUsageStatus.PENDING, BillingUsageStatus.FAILED),
                        Instant.now()
                );

        records.forEach(this::reportNow);
    }

    private String stripeIdentifier(BillingUsageRecord record) {
        return "ubi-telemetry-" + record.getTelemetryEventId();
    }

    private String truncate(String message) {
        if (message == null || message.length() <= 500) {
            return message;
        }

        return message.substring(0, 500);
    }
}
