package com.example.ubi.exception;

import java.util.List;

public class AnalyticsClarificationException extends RuntimeException {

    private final List<String> suggestions;

    public AnalyticsClarificationException(String message, List<String> suggestions) {
        super(message);
        this.suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    public List<String> suggestions() {
        return suggestions;
    }
}
