package com.example.ubi.analytics.ask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ubi.analytics.AnalyticsTestSupport;
import com.example.ubi.analytics.ast.AnalyticsAst;
import com.example.ubi.analytics.ast.ChartHint;
import com.example.ubi.analytics.execute.AnalyticsExecutor;
import com.example.ubi.analytics.execute.AnalyticsQueryResult;
import com.example.ubi.analytics.validate.AstValidator;
import com.example.ubi.dto.AnalyticsAskMeta;
import com.example.ubi.dto.AnalyticsAskRequest;
import com.example.ubi.dto.AnalyticsAskResponse;
import com.example.ubi.exception.AnalyticsClarificationException;
import com.example.ubi.exception.AnalyticsValidationException;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;

class AnalyticsAskServiceTest {

    @Test
    void happyPathPlansValidatesExecutesAndSummarizes() {
        AnalyticsAst ast = AnalyticsTestSupport.loadAst("/analytics/policies-avg-premium.json");
        QueryPlanner planner = mock(QueryPlanner.class);
        when(planner.plan(eq("avg premium by status"), isNull(), isNull()))
                .thenReturn(new PlannerResult(false, ast, "", List.of()));

        AnalyticsExecutor executor = mock(AnalyticsExecutor.class);
        when(executor.execute(any())).thenReturn(new AnalyticsQueryResult(
                "2026-09-12.1",
                "policies",
                ChartHint.BAR,
                List.of(new Document("status", "ACTIVE").append("avgPremium", 120))
        ));

        ResultSummarizer summarizer = mock(ResultSummarizer.class);
        when(summarizer.summarize(anyString(), eq("policies"), any(), isNull()))
                .thenReturn("Active policies average $120.");

        InMemoryAskQueryCache cache = new InMemoryAskQueryCache();
        AnalyticsAskService service = service(cache, planner, executor, summarizer);

        AnalyticsAskResponse response = service.ask(new AnalyticsAskRequest("avg premium by status", null, null));

        assertEquals("ok", response.status());
        assertEquals("Active policies average $120.", response.summary());
        assertEquals(ChartHint.BAR, response.chartHint());
        assertEquals(List.of("status", "avgPremium"), response.columns());
        assertEquals(1, response.rows().size());
        assertEquals("policies", response.ast().collection());
        assertFalse(response.meta().cached());
        assertFalse(response.meta().llmRepairUsed());
        assertEquals("policies", response.meta().collection());
        assertEquals("2026-09-12.1", response.meta().catalogVersion());
        assertEquals(1, response.meta().rowCount());
        assertEquals(1, cache.size());
        verify(planner, times(1)).plan(anyString(), any(), any());
        verify(executor).execute(any());
    }

    @Test
    void cacheHitSkipsPlannerAndExecutor() {
        AnalyticsAst ast = AnalyticsTestSupport.loadAst("/analytics/policies-avg-premium.json");
        InMemoryAskQueryCache cache = new InMemoryAskQueryCache();
        String key = AskCacheKey.hash("avg premium", AnalyticsTestSupport.CATALOG.version(), null);
        cache.put(key, new AnalyticsAskResponse(
                "ok",
                "cached summary",
                ChartHint.TABLE,
                List.of("status"),
                List.of(),
                ast,
                new AnalyticsAskMeta(false, 9L, true, "policies", "2026-09-12.1", 0)
        ));

        QueryPlanner planner = mock(QueryPlanner.class);
        AnalyticsExecutor executor = mock(AnalyticsExecutor.class);
        AnalyticsAskService service = service(cache, planner, executor, mock(ResultSummarizer.class));

        AnalyticsAskResponse response = service.ask(new AnalyticsAskRequest("AVG   premium", false, null));

        assertEquals("cached summary", response.summary());
        assertTrue(response.meta().cached());
        assertTrue(response.meta().llmRepairUsed());
        verify(planner, never()).plan(anyString(), any(), any());
        verify(executor, never()).execute(any());
    }

    @Test
    void skipCacheBypassesReadButStillStores() {
        AnalyticsAst ast = AnalyticsTestSupport.loadAst("/analytics/policies-avg-premium.json");
        InMemoryAskQueryCache cache = new InMemoryAskQueryCache();
        String key = AskCacheKey.hash("avg premium", AnalyticsTestSupport.CATALOG.version(), null);
        cache.put(key, new AnalyticsAskResponse(
                "ok",
                "stale",
                ChartHint.TABLE,
                List.of(),
                List.of(),
                ast,
                new AnalyticsAskMeta(false, 1L, false, "policies", "2026-09-12.1", 0)
        ));

        QueryPlanner planner = mock(QueryPlanner.class);
        when(planner.plan(eq("avg premium"), isNull(), isNull()))
                .thenReturn(new PlannerResult(false, ast, "", List.of()));
        AnalyticsExecutor executor = mock(AnalyticsExecutor.class);
        when(executor.execute(any())).thenReturn(new AnalyticsQueryResult(
                "2026-09-12.1", "policies", ChartHint.TABLE, List.of()
        ));
        ResultSummarizer summarizer = mock(ResultSummarizer.class);
        when(summarizer.summarize(anyString(), anyString(), any(), any())).thenReturn("fresh");

        AnalyticsAskResponse response = service(cache, planner, executor, summarizer)
                .ask(new AnalyticsAskRequest("avg premium", true, null));

        assertEquals("fresh", response.summary());
        assertFalse(response.meta().cached());
        assertEquals("fresh", cache.get(key).orElseThrow().summary());
        verify(planner).plan(eq("avg premium"), isNull(), isNull());
    }

    @Test
    void clarificationDoesNotExecute() {
        QueryPlanner planner = mock(QueryPlanner.class);
        when(planner.plan(anyString(), any(), any())).thenReturn(PlannerResult.clarification(
                "That needs two collections.",
                List.of("Sum usageCharge from billing_usage_records")
        ));
        AnalyticsExecutor executor = mock(AnalyticsExecutor.class);
        AnalyticsAskService service = service(new InMemoryAskQueryCache(), planner, executor, mock(ResultSummarizer.class));

        AnalyticsClarificationException ex = assertThrows(
                AnalyticsClarificationException.class,
                () -> service.ask(new AnalyticsAskRequest("unpaid premium for every policy", null, null))
        );
        assertEquals("That needs two collections.", ex.getMessage());
        assertEquals(List.of("Sum usageCharge from billing_usage_records"), ex.suggestions());
        verify(executor, never()).execute(any());
    }

    @Test
    void repairsOnceOnValidatorFailureThenSucceeds() {
        AnalyticsAst bad = new AnalyticsAst("users", List.of(), List.of(), List.of(), List.of(), List.of(), 10, ChartHint.TABLE);
        AnalyticsAst good = AnalyticsTestSupport.loadAst("/analytics/policies-avg-premium.json");

        QueryPlanner planner = mock(QueryPlanner.class);
        when(planner.plan(eq("avg premium"), isNull(), isNull()))
                .thenReturn(new PlannerResult(false, bad, "", List.of()));
        when(planner.plan(eq("avg premium"), isNull(), anyString()))
                .thenReturn(new PlannerResult(false, good, "", List.of()));

        AnalyticsExecutor executor = mock(AnalyticsExecutor.class);
        when(executor.execute(any())).thenReturn(new AnalyticsQueryResult(
                "2026-09-12.1", "policies", ChartHint.BAR, List.of()
        ));
        ResultSummarizer summarizer = mock(ResultSummarizer.class);
        when(summarizer.summarize(anyString(), anyString(), any(), any())).thenReturn("repaired");

        AnalyticsAskResponse response = service(new InMemoryAskQueryCache(), planner, executor, summarizer)
                .ask(new AnalyticsAskRequest("avg premium", null, null));

        assertTrue(response.meta().llmRepairUsed());
        assertEquals("repaired", response.summary());
        verify(planner, times(2)).plan(anyString(), any(), any());
        verify(executor, times(1)).execute(any());
    }

    @Test
    void secondValidatorFailureBecomesClarification() {
        AnalyticsAst bad = new AnalyticsAst("users", List.of(), List.of(), List.of(), List.of(), List.of(), 10, ChartHint.TABLE);
        QueryPlanner planner = mock(QueryPlanner.class);
        when(planner.plan(anyString(), any(), any())).thenReturn(new PlannerResult(false, bad, "", List.of()));
        AnalyticsExecutor executor = mock(AnalyticsExecutor.class);

        AnalyticsClarificationException ex = assertThrows(
                AnalyticsClarificationException.class,
                () -> service(new InMemoryAskQueryCache(), planner, executor, mock(ResultSummarizer.class))
                        .ask(new AnalyticsAskRequest("junk", null, null))
        );
        assertTrue(ex.getMessage().contains("could not turn that question"));
        assertFalse(ex.suggestions().isEmpty());
        verify(executor, never()).execute(any());
        verify(planner, times(2)).plan(anyString(), any(), any());
    }

    @Test
    void blankQuestionIsValidationError() {
        AnalyticsAskService service = service(
                new InMemoryAskQueryCache(),
                mock(QueryPlanner.class),
                mock(AnalyticsExecutor.class),
                mock(ResultSummarizer.class)
        );
        assertThrows(AnalyticsValidationException.class, () -> service.ask(new AnalyticsAskRequest("   ", null, null)));
        assertThrows(AnalyticsValidationException.class, () -> service.ask(null));
    }

    private static AnalyticsAskService service(
            AskQueryCache cache,
            QueryPlanner planner,
            AnalyticsExecutor executor,
            ResultSummarizer summarizer
    ) {
        return new AnalyticsAskService(
                cache,
                planner,
                new AstValidator(AnalyticsTestSupport.CATALOG),
                executor,
                summarizer,
                AnalyticsTestSupport.CATALOG
        );
    }
}
