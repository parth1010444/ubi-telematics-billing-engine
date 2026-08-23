package com.example.ubi.application;

import com.example.ubi.domain.model.Policy;
import com.example.ubi.domain.model.PolicyStatus;
import com.example.ubi.domain.model.TelemetryEvent;
import com.example.ubi.domain.repository.PolicyRepository;
import com.example.ubi.domain.repository.TelemetryEventRepository;
import com.example.ubi.dto.TelemetryHistoryItemResponse;
import com.example.ubi.dto.TelemetryIngestRequest;
import com.example.ubi.exception.InvalidTelemetryPayloadException;
import com.example.ubi.exception.ResourceNotFoundException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TelemetryIngestionService {

    private static final Logger LOGGER = LoggerFactory.getLogger(TelemetryIngestionService.class);

    private final PolicyRepository policyRepository;
    private final TelemetryEventRepository telemetryEventRepository;
    private final RiskCalculationService riskCalculationService;
    private final BillingOutboxService billingOutboxService;

    public TelemetryIngestionService(
            PolicyRepository policyRepository,
            TelemetryEventRepository telemetryEventRepository,
            RiskCalculationService riskCalculationService,
            BillingOutboxService billingOutboxService
    ) {
        this.policyRepository = policyRepository;
        this.telemetryEventRepository = telemetryEventRepository;
        this.riskCalculationService = riskCalculationService;
        this.billingOutboxService = billingOutboxService;
    }

    public void ingest(TelemetryIngestRequest request) {
        if (telemetryEventRepository.existsById(request.getEventId())) {
            LOGGER.info(
                    "Skipping duplicate telemetry eventId={} for policyId={}",
                    request.getEventId(),
                    request.getPolicyId()
            );
            return;
        }

        Policy policy = policyRepository.findById(request.getPolicyId())
                .filter(existingPolicy -> existingPolicy.getStatus() == PolicyStatus.ACTIVE)
                .orElseThrow(() -> new InvalidTelemetryPayloadException("Telemetry can only be ingested for an active policy"));

        TelemetryEvent event = toTelemetryEvent(request);
        try {
            telemetryEventRepository.save(event);
        } catch (DuplicateKeyException ignored) {
            LOGGER.info(
                    "Skipping duplicate telemetry eventId={} for policyId={} (race)",
                    request.getEventId(),
                    request.getPolicyId()
            );
            return;
        }

        RiskCalculationService.UsageChargeBreakdown breakdown =
                riskCalculationService.calculateUsageChargeBreakdown(event);
        BigDecimal usageCharge = breakdown.total();
        int billableUsageUnits = usageCharge.setScale(0, RoundingMode.CEILING).intValueExact();

        BigDecimal accruedPremium = policy.getBasePremium().add(
                telemetryEventRepository.findByPolicyIdOrderByTimestampDesc(policy.getPolicyId()).stream()
                        .map(riskCalculationService::calculateUsageCharge)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
        );

        LOGGER.info(
                "Usage-based monthly premium update: policyId={} basePremium={} eventUsageCharge={} "
                        + "distanceCost={} hardBrakePenalty={} billableUsageUnits={} accruedMonthlyPremium≈{}",
                policy.getPolicyId(),
                policy.getBasePremium(),
                usageCharge,
                breakdown.distanceCost(),
                breakdown.behaviorPenalty(),
                billableUsageUnits,
                accruedPremium
        );

        billingOutboxService.reportNow(billingOutboxService.createPendingRecord(
                policy,
                event,
                usageCharge,
                billableUsageUnits
        ));
    }

    public List<TelemetryHistoryItemResponse> history(String policyId) {
        if (!policyRepository.existsById(policyId)) {
            throw new ResourceNotFoundException("Policy not found");
        }

        return telemetryEventRepository.findByPolicyIdOrderByTimestampDesc(policyId).stream()
                .map(event -> TelemetryHistoryItemResponse.from(
                        event,
                        riskCalculationService.calculateUsageCharge(event),
                        riskCalculationService.classifyRiskLevel(event)
                ))
                .toList();
    }

    private TelemetryEvent toTelemetryEvent(TelemetryIngestRequest request) {
        TelemetryEvent event = new TelemetryEvent();
        event.setEventId(request.getEventId());
        event.setPolicyId(request.getPolicyId());
        event.setTimestamp(request.getTimestamp());
        event.setSpeedKmh(request.getSpeedKmh());
        event.setHardBraking(Boolean.TRUE.equals(request.isHardBraking()));
        event.setDistanceTraveledKm(request.getDistanceTraveledKm());
        return event;
    }
}
