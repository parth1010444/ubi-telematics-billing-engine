package com.example.ubi.analytics.ast;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Locale;

public enum SortDirection {
    ASC,
    DESC;

    @JsonValue
    public String toJson() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static SortDirection fromJson(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return SortDirection.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unknown sort direction: " + raw + " (allowed: asc, desc)");
        }
    }
}
