package com.example.ubi.analytics.ask;

import com.example.ubi.analytics.ast.AnalyticsAst;
import java.util.List;
import java.util.Locale;

public record PlannerResult(
        boolean needsClarification,
        AnalyticsAst ast,
        String message,
        List<String> suggestions
) {
    public PlannerResult {
        suggestions = suggestions == null ? List.of() : List.copyOf(suggestions);
        if (message == null) {
            message = "";
        }
    }

    public static PlannerResult from(PlannerOutput output) {
        if (output == null) {
            return clarification(
                    "I could not plan a query for that question.",
                    List.of(
                            "Rephrase using one collection: policies, telemetry_events, or billing_usage_records",
                            "Ask for a count, sum, average, or listing of NL-exposed fields"
                    )
            );
        }
        boolean clarify = isClarification(output.status()) || output.ast() == null;
        if (clarify) {
            String message = output.message() == null || output.message().isBlank()
                    ? "That question needs more detail, or it requires more than one collection."
                    : output.message();
            List<String> suggestions = output.suggestions().isEmpty()
                    ? List.of(
                            "Query policies (status, basePremium, paidAmountCents)",
                            "Query telemetry_events (speedKmh, isHardBraking, distanceTraveledKm)",
                            "Query billing_usage_records (usageCharge, status)"
                    )
                    : output.suggestions();
            return clarification(message, suggestions);
        }
        return new PlannerResult(false, output.ast(), output.message(), output.suggestions());
    }

    public static PlannerResult clarification(String message, List<String> suggestions) {
        return new PlannerResult(true, null, message, suggestions);
    }

    private static boolean isClarification(String status) {
        if (status == null || status.isBlank()) {
            return false;
        }
        String normalized = status.trim().toLowerCase(Locale.ROOT).replace('-', '_');
        return "needs_clarification".equals(normalized) || "clarification".equals(normalized);
    }
}
