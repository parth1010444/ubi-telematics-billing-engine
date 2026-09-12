package com.example.ubi.analytics.ask;

import com.example.ubi.analytics.ast.AnalyticsLimits;
import com.example.ubi.analytics.catalog.SchemaCatalog;
import com.example.ubi.exception.AnalyticsLlmUnavailableException;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Question + catalog → structured {@link PlannerResult} via Gemini.
 */
@Component
public class QueryPlanner {

    private static final Logger LOGGER = LoggerFactory.getLogger(QueryPlanner.class);

    private final AnalyticsLlmClient llmClient;
    private final SchemaCatalog catalog;

    public QueryPlanner(AnalyticsLlmClient llmClient, SchemaCatalog catalog) {
        this.llmClient = llmClient;
        this.catalog = catalog;
    }

    public PlannerResult plan(String question, String locale, String previousError) {
        if (llmClient == null) {
            throw new AnalyticsLlmUnavailableException(
                    "Gemini is not configured. Set GEMINI_API_KEY (or SPRING_AI_GOOGLE_GENAI_API_KEY) "
                            + "and restart. See docs/analytics-phase-b.md."
            );
        }
        String system = systemPrompt();
        String user = userPrompt(question, locale, previousError);
        try {
            PlannerOutput output = llmClient.completeJson(system, user, PlannerOutput.class);
            return PlannerResult.from(output);
        } catch (AnalyticsLlmUnavailableException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            LOGGER.warn("Planner structured output failed: {}", exception.getMessage());
            return PlannerResult.clarification(
                    "I could not plan a query for that question. Try rephrasing, or ask about a single collection.",
                    List.of(
                            "Query policies (status, basePremium, paidAmountCents)",
                            "Query telemetry_events (speedKmh, isHardBraking, distanceTraveledKm)",
                            "Query billing_usage_records (usageCharge, status)"
                    )
            );
        }
    }

    String systemPrompt() {
        return """
                You are the UBI telematics analytics query planner.
                Convert a natural-language question into ONE AnalyticsAst JSON object, or ask for clarification.

                Hard rules:
                - Query exactly one collection. Never invent collections or fields.
                - Never use $lookup, joins, or multi-collection metrics.
                - Use only NL-exposed fields (nlExposed=true). Never use stripeCustomerId or lastCheckoutSessionId.
                - Identifiers must match [A-Za-z][A-Za-z0-9_]* (no $ or .).
                - filters max %d, groupBy max %d, metrics max %d, sort max %d, having max %d.
                - limit 1–%d (default %d).
                - filter ops: eq, neq, gt, gte, lt, lte, in, exists.
                - metric fns: count, sum, avg, min, max. count may omit field.
                - Conditional count/sum: set filterField + filterEquals (compiles to $cond).
                - groupBy requires at least one metric. having/sort-on-aggregates must use metric aliases or groupBy fields.
                - INSTANT filters use ISO-8601 strings. ENUM filters use the allowed values exactly.
                - chartHint: table, bar, line, or pie.

                Accrued / unpaid / "how much do they owe":
                - accruedPremium and unpaidAmountCents are NOT stored as single-collection fields.
                - They require policies.basePremium + sum of usage charges (+ paidAmountCents).
                - That spans collections. You MUST return needs_clarification with suggestions
                  that stay on one collection (e.g. list policies' basePremium and paidAmountCents,
                  or sum billing_usage_records.usageCharge by policyId).

                If the question is ambiguous, names an unknown field, or needs multiple collections,
                return status=needs_clarification with a short message and 2–4 concrete suggestions.

                Output JSON only, matching:
                {
                  "status": "ok" | "needs_clarification",
                  "ast": { "collection", "filters", "groupBy", "metrics", "having", "sort", "limit", "chartHint" } | null,
                  "message": "string or null",
                  "suggestions": ["..."]
                }
                When status=ok, ast is required and message/suggestions may be empty.

                Schema catalog:
                """.formatted(
                        AnalyticsLimits.MAX_FILTERS,
                        AnalyticsLimits.MAX_GROUP_BY,
                        AnalyticsLimits.MAX_METRICS,
                        AnalyticsLimits.MAX_SORT,
                        AnalyticsLimits.MAX_HAVING,
                        AnalyticsLimits.MAX_LIMIT,
                        AnalyticsLimits.DEFAULT_LIMIT
                )
                + CatalogPromptRenderer.render(catalog);
    }

    static String userPrompt(String question, String locale, String previousError) {
        StringBuilder user = new StringBuilder();
        if (locale != null && !locale.isBlank()) {
            user.append("locale: ").append(locale.trim()).append('\n');
        }
        user.append("question: ").append(question == null ? "" : question.trim()).append('\n');
        if (previousError != null && !previousError.isBlank()) {
            user.append('\n');
            user.append("The previous AST failed validation. Fix the AST or return needs_clarification.\n");
            user.append("validationErrors: ").append(previousError.trim()).append('\n');
        }
        return user.toString();
    }
}
