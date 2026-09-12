package com.example.ubi.analytics.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Deterministic analytics query. Phase A is AST-in / pipeline-out — no natural language.
 *
 * <pre>
 * {
 *   "collection": "telemetry_events",
 *   "filters": [{ "field": "speedKmh", "op": "gte", "value": 100 }],
 *   "groupBy": ["policyId"],
 *   "metrics": [
 *     { "alias": "events", "fn": "count" },
 *     { "alias": "hardBrakes", "fn": "sum", "field": "isHardBraking",
 *       "filterField": "isHardBraking", "filterEquals": true }
 *   ],
 *   "having": [{ "field": "events", "op": "gt", "value": 10 }],
 *   "sort": [{ "field": "events", "direction": "desc" }],
 *   "limit": 20,
 *   "chartHint": "bar"
 * }
 * </pre>
 */
@JsonIgnoreProperties(ignoreUnknown = false)
public record AnalyticsAst(
        String collection,
        List<FilterExpr> filters,
        List<String> groupBy,
        List<MetricExpr> metrics,
        List<FilterExpr> having,
        List<SortExpr> sort,
        Integer limit,
        ChartHint chartHint
) {
    public AnalyticsAst {
        filters = copy(filters);
        groupBy = copy(groupBy);
        metrics = copy(metrics);
        having = copy(having);
        sort = copy(sort);
        if (chartHint == null) {
            chartHint = ChartHint.TABLE;
        }
    }

    public AnalyticsAst withLimit(int normalizedLimit) {
        return new AnalyticsAst(
                collection,
                filters,
                groupBy,
                metrics,
                having,
                sort,
                normalizedLimit,
                chartHint
        );
    }

    public boolean aggregating() {
        return !metrics.isEmpty() || !groupBy.isEmpty();
    }

    private static <T> List<T> copy(List<T> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
