package com.example.ubi.analytics.ask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ResultSummarizerTest {

    @Test
    void usesLlmSummaryWhenPresent() {
        AnalyticsLlmClient llm = mock(AnalyticsLlmClient.class);
        when(llm.completeText(anyString(), anyString())).thenReturn("  Two policies averaged $120.  ");
        ResultSummarizer summarizer = new ResultSummarizer(llm, 8, 80);

        String summary = summarizer.summarize(
                "avg premium",
                "policies",
                List.of(Map.of("status", "ACTIVE", "avgPremium", 120)),
                "en"
        );
        assertEquals("Two policies averaged $120.", summary);
    }

    @Test
    void fallsBackWhenLlmFails() {
        AnalyticsLlmClient llm = mock(AnalyticsLlmClient.class);
        when(llm.completeText(anyString(), anyString())).thenThrow(new RuntimeException("quota"));
        ResultSummarizer summarizer = new ResultSummarizer(llm, 8, 80);

        String summary = summarizer.summarize(
                "avg premium",
                "policies",
                List.of(Map.of("status", "ACTIVE")),
                null
        );
        assertEquals("Returned 1 row(s) from policies for: avg premium", summary);
    }

    @Test
    void truncatesRowsAndValues() {
        ResultSummarizer summarizer = new ResultSummarizer(mock(AnalyticsLlmClient.class), 2, 5);
        String truncated = summarizer.truncateRows(List.of(
                Map.of("name", "abcdef"),
                Map.of("n", 1),
                Map.of("n", 2)
        ));
        assertTrue(truncated.contains("abcde…"));
        assertTrue(truncated.contains("1 more rows omitted"));
    }

    @Test
    void emptyRowsFallback() {
        assertEquals(
                "No rows matched that question on telemetry_events.",
                ResultSummarizer.fallback("hard brakes", "telemetry_events", List.of())
        );
    }
}
