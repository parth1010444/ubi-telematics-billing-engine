package com.example.ubi.analytics.catalog;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public record FieldSchema(
        String name,
        String mongoName,
        FieldType type,
        boolean nlExposed,
        String description,
        List<String> enumValues,
        List<String> aliases
) {
    public FieldSchema {
        if (enumValues == null) {
            enumValues = List.of();
        } else {
            enumValues = List.copyOf(enumValues);
        }
        if (aliases == null) {
            aliases = List.of();
        } else {
            aliases = List.copyOf(aliases);
        }
        if (description == null) {
            description = "";
        }
    }

    public boolean matches(String candidate) {
        if (candidate == null) {
            return false;
        }
        if (name.equals(candidate) || mongoName.equals(candidate)) {
            return true;
        }
        return aliases.contains(candidate);
    }

    public boolean isNumeric() {
        return type == FieldType.INTEGER || type == FieldType.LONG || type == FieldType.DECIMAL;
    }

    public boolean isAllowedEnumValue(Object value) {
        if (type != FieldType.ENUM || enumValues.isEmpty() || value == null) {
            return true;
        }
        String raw = String.valueOf(value).trim().toUpperCase(Locale.ROOT);
        Set<String> allowed = Set.copyOf(enumValues);
        return allowed.contains(raw);
    }
}
