package com.example.ubi.analytics.ast;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum MetricFn {
    COUNT,
    SUM,
    AVG,
    MIN,
    MAX;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static MetricFn fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return MetricFn.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unknown metric fn: " + raw + " (allowed: " + allowed() + ")"
            );
        }
    }

    public boolean requiresField() {
        return this != COUNT;
    }

    public static String allowed() {
        return Arrays.stream(values()).map(MetricFn::toJson).collect(Collectors.joining(", "));
    }
}
