package com.example.ubi.analytics.ast;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = false)
public record SortExpr(
        String field,
        SortDirection direction
) {
    public SortExpr {
        if (direction == null) {
            direction = SortDirection.ASC;
        }
    }
}
