package com.example.ubi.api;

import com.example.ubi.application.TelemetryIngestionService;
import com.example.ubi.dto.TelemetryIngestRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/telemetry")
public class TelemetryController {

    private final TelemetryIngestionService telemetryIngestionService;

    public TelemetryController(TelemetryIngestionService telemetryIngestionService) {
        this.telemetryIngestionService = telemetryIngestionService;
    }

    @PostMapping("/ingest")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void ingest(@Valid @RequestBody TelemetryIngestRequest request) {
        telemetryIngestionService.ingest(request);
    }
}
