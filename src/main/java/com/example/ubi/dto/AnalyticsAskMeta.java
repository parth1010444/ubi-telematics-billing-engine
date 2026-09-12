package com.example.ubi.dto;

public record AnalyticsAskMeta(
        boolean cached,
        long latencyMs,
        boolean llmRepairUsed,
        String collection,
        String catalogVersion,
        int rowCount
) {
}
