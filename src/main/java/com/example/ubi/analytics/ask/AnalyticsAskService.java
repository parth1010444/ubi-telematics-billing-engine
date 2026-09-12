package com.example.ubi.analytics.ask;

import com.example.ubi.analytics.ast.AnalyticsAst;
import com.example.ubi.analytics.catalog.SchemaCatalog;
import com.example.ubi.analytics.execute.AnalyticsExecutor;
import com.example.ubi.analytics.execute.AnalyticsQueryResult;
import com.example.ubi.analytics.validate.AstValidator;
import com.example.ubi.dto.AnalyticsAskMeta;
import com.example.ubi.dto.AnalyticsAskRequest;
import com.example.ubi.dto.AnalyticsAskResponse;
import com.example.ubi.exception.AnalyticsClarificationException;
import com.example.ubi.exception.AnalyticsValidationException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * NL ask pipeline: cache → plan → validate → (1 repair) → execute → summarize → cache.
 */
@Service
public class AnalyticsAskService {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyticsAskService.class);
    static final int MAX_QUESTION_CHARS = 2000;

    private static final List<String> REPAIR_SUGGESTIONS = List.of(
            "Ask about one collection only: policies, telemetry_events, or billing_usage_records",
            "Use NL-exposed fields (for example speedKmh, isHardBraking, status, usageCharge)",
            "v1 cannot join collections or compute full accrued/unpaid premium across collections"
    );

    private final AskQueryCache cache;
    private final QueryPlanner planner;
    private final AstValidator validator;
    private final AnalyticsExecutor executor;
    private final ResultSummarizer summarizer;
    private final SchemaCatalog catalog;

    public AnalyticsAskService(
            AskQueryCache cache,
            QueryPlanner planner,
            AstValidator validator,
            AnalyticsExecutor executor,
            ResultSummarizer summarizer,
            SchemaCatalog catalog
    ) {
        this.cache = cache;
        this.planner = planner;
        this.validator = validator;
        this.executor = executor;
        this.summarizer = summarizer;
        this.catalog = catalog;
    }

    public AnalyticsAskResponse ask(AnalyticsAskRequest request) {
        long started = System.nanoTime();
        String question = requireQuestion(request);
        String locale = request == null ? null : request.locale();
        boolean skipCache = request != null && request.skipCacheRequested();
        String cacheKey = AskCacheKey.hash(question, catalog.version(), locale);

        if (!skipCache) {
            var cached = cache.get(cacheKey);
            if (cached.isPresent()) {
                AnalyticsAskResponse hit = cached.get();
                long latencyMs = elapsedMs(started);
                AnalyticsAskMeta meta = hit.meta() == null
                        ? new AnalyticsAskMeta(true, latencyMs, false, "", catalog.version(), 0)
                        : new AnalyticsAskMeta(
                                true,
                                latencyMs,
                                hit.meta().llmRepairUsed(),
                                hit.meta().collection(),
                                hit.meta().catalogVersion(),
                                hit.meta().rowCount()
                        );
                return hit.withMeta(meta);
            }
        }

        PlannedAst planned = planAndValidate(question, locale);
        AnalyticsQueryResult executed = executor.execute(planned.ast());
        List<Map<String, Object>> rows = toRows(executed.rows());
        List<String> columns = columnsOf(rows, planned.ast());
        String summary = summarizer.summarize(question, executed.collection(), rows, locale);

        AnalyticsAskMeta meta = new AnalyticsAskMeta(
                false,
                elapsedMs(started),
                planned.repairUsed(),
                executed.collection(),
                executed.catalogVersion(),
                rows.size()
        );
        AnalyticsAskResponse response = new AnalyticsAskResponse(
                AnalyticsAskResponse.STATUS_OK,
                summary,
                executed.chartHint(),
                columns,
                rows,
                planned.ast(),
                meta
        );
        cache.put(cacheKey, response);
        return response;
    }

    private PlannedAst planAndValidate(String question, String locale) {
        PlannerResult first = planner.plan(question, locale, null);
        throwIfClarification(first);
        try {
            return new PlannedAst(validator.validateAndNormalize(first.ast()), false);
        } catch (AnalyticsValidationException firstFailure) {
            LOGGER.info("Ask AST failed validation; attempting one repair: {}", firstFailure.getMessage());
            PlannerResult repaired = planner.plan(question, locale, firstFailure.getMessage());
            throwIfClarification(repaired);
            try {
                return new PlannedAst(validator.validateAndNormalize(repaired.ast()), true);
            } catch (AnalyticsValidationException secondFailure) {
                throw new AnalyticsClarificationException(
                        "I could not turn that question into a valid single-collection query. "
                                + secondFailure.getMessage(),
                        REPAIR_SUGGESTIONS
                );
            }
        }
    }

    private static void throwIfClarification(PlannerResult result) {
        if (result != null && result.needsClarification()) {
            throw new AnalyticsClarificationException(result.message(), result.suggestions());
        }
        if (result == null || result.ast() == null) {
            throw new AnalyticsClarificationException(
                    "I could not plan a query for that question.",
                    REPAIR_SUGGESTIONS
            );
        }
    }

    static String requireQuestion(AnalyticsAskRequest request) {
        if (request == null || request.question() == null || request.question().isBlank()) {
            throw new AnalyticsValidationException(List.of("question is required"));
        }
        String question = request.question().trim();
        if (question.length() > MAX_QUESTION_CHARS) {
            throw new AnalyticsValidationException(List.of(
                    "question exceeds max of " + MAX_QUESTION_CHARS + " characters"
            ));
        }
        return question;
    }

    static List<Map<String, Object>> toRows(List<Document> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        List<Map<String, Object>> rows = new ArrayList<>(documents.size());
        for (Document document : documents) {
            rows.add(document == null ? Map.of() : new LinkedHashMap<>(document));
        }
        return List.copyOf(rows);
    }

    static List<String> columnsOf(List<Map<String, Object>> rows, AnalyticsAst ast) {
        if (rows != null && !rows.isEmpty()) {
            Set<String> columns = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                if (row != null) {
                    columns.addAll(row.keySet());
                }
            }
            return List.copyOf(columns);
        }
        if (ast == null) {
            return List.of();
        }
        List<String> columns = new ArrayList<>(ast.groupBy());
        ast.metrics().forEach(metric -> {
            if (metric != null && metric.alias() != null && !metric.alias().isBlank()) {
                columns.add(metric.alias());
            }
        });
        return List.copyOf(columns);
    }

    private static long elapsedMs(long startedNanos) {
        return Math.max(0L, (System.nanoTime() - startedNanos) / 1_000_000L);
    }

    private record PlannedAst(AnalyticsAst ast, boolean repairUsed) {
    }
}
