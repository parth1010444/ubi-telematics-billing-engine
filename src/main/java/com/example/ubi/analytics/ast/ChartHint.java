package com.example.ubi.analytics.ast;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

public enum ChartHint {
    TABLE,
    BAR,
    LINE,
    PIE;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static ChartHint fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return ChartHint.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException(
                    "Unknown chartHint: " + raw + " (allowed: " + allowed() + ")"
            );
        }
    }

    public static String allowed() {
        return Arrays.stream(values()).map(ChartHint::toJson).collect(Collectors.joining(", "));
    }
}
