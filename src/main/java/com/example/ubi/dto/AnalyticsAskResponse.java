package com.example.ubi.dto;

import com.example.ubi.analytics.ast.AnalyticsAst;
import com.example.ubi.analytics.ast.ChartHint;
import java.util.List;
import java.util.Map;

public record AnalyticsAskResponse(
        String status,
        String summary,
        ChartHint chartHint,
        List<String> columns,
        List<Map<String, Object>> rows,
        AnalyticsAst ast,
        AnalyticsAskMeta meta
) {
    public static final String STATUS_OK = "ok";

    public AnalyticsAskResponse {
        columns = columns == null ? List.of() : List.copyOf(columns);
        rows = rows == null ? List.of() : List.copyOf(rows);
        if (status == null || status.isBlank()) {
            status = STATUS_OK;
        }
        if (summary == null) {
            summary = "";
        }
        if (chartHint == null) {
            chartHint = ChartHint.TABLE;
        }
    }

    public AnalyticsAskResponse withMeta(AnalyticsAskMeta updated) {
        return new AnalyticsAskResponse(status, summary, chartHint, columns, rows, ast, updated);
    }
}
