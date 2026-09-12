package com.example.ubi.dto;

import com.example.ubi.analytics.ast.ChartHint;
import com.example.ubi.analytics.execute.AnalyticsQueryResult;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record AnalyticsExecuteResponse(
        String catalogVersion,
        String collection,
        ChartHint chartHint,
        List<Map<String, Object>> rows
) {
    public static AnalyticsExecuteResponse from(AnalyticsQueryResult result) {
        List<Map<String, Object>> rows = result.rows().stream()
                .map(document -> (Map<String, Object>) new LinkedHashMap<String, Object>(document))
                .toList();
        return new AnalyticsExecuteResponse(
                result.catalogVersion(),
                result.collection(),
                result.chartHint(),
                rows
        );
    }
}
