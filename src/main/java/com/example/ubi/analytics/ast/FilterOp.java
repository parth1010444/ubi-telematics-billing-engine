package com.example.ubi.analytics.ast;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum FilterOp {
    EQ,
    NEQ,
    GT,
    GTE,
    LT,
    LTE,
    IN,
    EXISTS;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static FilterOp fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return FilterOp.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unknown filter op: " + raw + " (allowed: " + allowed() + ")"
            );
        }
    }

    public static String allowed() {
        return Arrays.stream(values()).map(FilterOp::toJson).collect(Collectors.joining(", "));
    }
}
