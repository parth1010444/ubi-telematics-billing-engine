package com.example.ubi.analytics.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record FilterExpr(
        String field,
        FilterOp op,
        Object value
) {
}
