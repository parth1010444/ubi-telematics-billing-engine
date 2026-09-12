package com.example.ubi.analytics.ask;

import com.example.ubi.analytics.ast.AnalyticsAst;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

/**
 * Gemini structured output for NL → AST (or clarification).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PlannerOutput(
        String status,
        AnalyticsAst ast,
        String message,
        List<String> suggestions
) {
    public PlannerOutput {
        if (suggestions == null) {
            suggestions = List.of();
        } else {
            suggestions = List.copyOf(suggestions);
        }
    }
}
