package com.example.ubi.exception;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AnalyticsValidationException extends RuntimeException {

    private final List<String> errors;

    public AnalyticsValidationException(List<String> errors) {
        super(errors == null || errors.isEmpty() ? "Invalid analytics AST" : String.join("; ", errors));
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public List<String> getErrors() {
        return errors;
    }

    public Map<String, String> fieldErrors() {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < errors.size(); i++) {
            map.put("error[" + i + "]", errors.get(i));
        }
        return map;
    }
}
