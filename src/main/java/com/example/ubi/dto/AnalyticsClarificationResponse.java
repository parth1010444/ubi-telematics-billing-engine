package com.example.ubi.dto;

import java.util.List;

public record AnalyticsClarificationResponse(
        String status,
        String message,
        List<String> suggestions
) {
    public static final String STATUS_NEEDS_CLARIFICATION = "needs_clarification";

    public AnalyticsClarificationResponse {
        if (status == null || status.isBlank()) {
            status = STATUS_NEEDS_CLARIFICATION;
        }
        if (message == null) {
            message = "";
        }
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
    }

    public static AnalyticsClarificationResponse of(String message, List<String> suggestions) {
        return new AnalyticsClarificationResponse(STATUS_NEEDS_CLARIFICATION, message, suggestions);
    }
}
