package com.example.ubi.application;

import com.example.ubi.domain.model.Policy;
import com.example.ubi.domain.model.PolicyStatus;
import com.example.ubi.domain.model.TelemetryEvent;
import com.example.ubi.domain.repository.PolicyRepository;
import com.example.ubi.domain.repository.TelemetryEventRepository;
import com.example.ubi.dto.TelemetryIngestRequest;
import com.example.ubi.exception.InvalidTelemetryPayloadException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

@Service
public class TelemetryIngestionService {

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
            return;
        }

        Policy policy = policyRepository.findById(request.getPolicyId())
                .filter(existingPolicy -> existingPolicy.getStatus() == PolicyStatus.ACTIVE)
                .orElseThrow(() -> new InvalidTelemetryPayloadException("Telemetry can only be ingested for an active policy"));

        TelemetryEvent event = toTelemetryEvent(request);
        try {
            telemetryEventRepository.save(event);
        } catch (DuplicateKeyException ignored) {
            return;
        }

        BigDecimal usageCharge = riskCalculationService.calculateUsageCharge(event);
        int billableUsageUnits = usageCharge.setScale(0, RoundingMode.CEILING).intValueExact();
        billingOutboxService.reportNow(billingOutboxService.createPendingRecord(
                policy,
                event,
                usageCharge,
                billableUsageUnits
        ));
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
