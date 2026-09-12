package com.example.ubi.analytics.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record MetricExpr(
        String alias,
        MetricFn fn,
        String field,
        String filterField,
        Object filterEquals
) {
    public boolean hasConditionalFilter() {
        return filterField != null && !filterField.isBlank();
    }
}
