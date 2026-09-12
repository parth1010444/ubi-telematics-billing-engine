package com.example.ubi.analytics.ask;

import com.example.ubi.config.AnalyticsProperties;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * 1–2 sentence natural-language summary of truncated analytics rows.
 */
@Component
public class ResultSummarizer {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResultSummarizer.class);

    private final AnalyticsLlmClient llmClient;
    private final int maxRows;
    private final int maxValueChars;

    public ResultSummarizer(AnalyticsLlmClient llmClient, AnalyticsProperties properties) {
        this.llmClient = llmClient;
        this.maxRows = properties.llm().summaryMaxRows();
        this.maxValueChars = properties.llm().summaryMaxValueChars();
    }

    public ResultSummarizer(AnalyticsLlmClient llmClient, int maxRows, int maxValueChars) {
        this.llmClient = llmClient;
        this.maxRows = maxRows <= 0 ? AnalyticsProperties.DEFAULT_SUMMARY_MAX_ROWS : maxRows;
        this.maxValueChars = maxValueChars <= 0
                ? AnalyticsProperties.DEFAULT_SUMMARY_MAX_VALUE_CHARS
                : maxValueChars;
    }

    public String summarize(String question, String collection, List<Map<String, Object>> rows, String locale) {
        String truncated = truncateRows(rows);
        if (llmClient == null) {
            return fallback(question, collection, rows);
        }
        try {
            String text = llmClient.completeText(systemPrompt(locale), userPrompt(question, collection, truncated));
            if (text == null || text.isBlank()) {
                return fallback(question, collection, rows);
            }
            return text.trim();
        } catch (RuntimeException exception) {
            LOGGER.warn("Result summarizer failed, using fallback: {}", exception.getMessage());
            return fallback(question, collection, rows);
        }
    }

    public String truncateRows(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "[]";
        }
        int count = Math.min(rows.size(), maxRows);
        StringJoiner joiner = new StringJoiner(", ", "[", "]");
        for (int i = 0; i < count; i++) {
            joiner.add(truncateRow(rows.get(i)));
        }
        if (rows.size() > maxRows) {
            return joiner + " …(" + (rows.size() - maxRows) + " more rows omitted)";
        }
        return joiner.toString();
    }

    private String truncateRow(Map<String, Object> row) {
        if (row == null || row.isEmpty()) {
            return "{}";
        }
        StringJoiner joiner = new StringJoiner(", ", "{", "}");
        row.forEach((key, value) -> joiner.add(key + "=" + truncateValue(value)));
        return joiner.toString();
    }

    private String truncateValue(Object value) {
        String raw = String.valueOf(value);
        if (raw.length() <= maxValueChars) {
            return raw;
        }
        return raw.substring(0, maxValueChars) + "…";
    }

    static String fallback(String question, String collection, List<Map<String, Object>> rows) {
        int n = rows == null ? 0 : rows.size();
        String coll = collection == null || collection.isBlank() ? "the catalog" : collection;
        if (n == 0) {
            return "No rows matched that question on " + coll + ".";
        }
        String q = question == null ? "" : question.trim();
        if (q.length() > 120) {
            q = q.substring(0, 117) + "…";
        }
        if (q.isEmpty()) {
            return "Returned " + n + " row(s) from " + coll + ".";
        }
        return "Returned " + n + " row(s) from " + coll + " for: " + q;
    }

    static String systemPrompt(String locale) {
        String language = locale == null || locale.isBlank()
                ? "the same language as the question"
                : locale.trim();
        return """
                You write a factual 1–2 sentence summary of analytics query results.
                Do not invent numbers that are not in the rows.
                Do not mention Mongo, AST, pipelines, or internal field ids unless they appear in the rows.
                Reply in %s.
                """.formatted(language);
    }

    static String userPrompt(String question, String collection, String truncatedRows) {
        return """
                question: %s
                collection: %s
                rows (truncated): %s
                """.formatted(
                question == null ? "" : question.trim(),
                collection == null ? "" : collection,
                truncatedRows
        );
    }
}
