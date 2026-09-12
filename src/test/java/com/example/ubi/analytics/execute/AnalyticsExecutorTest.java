package com.example.ubi.analytics.execute;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ubi.analytics.AnalyticsTestSupport;
import com.example.ubi.analytics.allowlist.PipelineAllowlist;
import com.example.ubi.analytics.ast.AnalyticsAst;
import com.example.ubi.analytics.ast.ChartHint;
import com.example.ubi.analytics.compile.MongoPipelineCompiler;
import com.example.ubi.analytics.validate.AstValidator;
import com.example.ubi.config.AnalyticsProperties;
import com.example.ubi.exception.AnalyticsValidationException;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;

class AnalyticsExecutorTest {

    @Test
    void validatesCompilesAllowlistsAndRunsWithMaxTime() {
        RecordingRunner runner = new RecordingRunner(List.of(
                new Document("policyId", "p1").append("events", 7)
        ));
        AnalyticsExecutor executor = executor(runner);

        AnalyticsAst ast = AnalyticsTestSupport.loadAst("/analytics/telemetry-hard-brakes.json");
        AnalyticsQueryResult result = executor.execute(ast);

        assertEquals("2026-09-12.1", result.catalogVersion());
        assertEquals("telemetry_events", result.collection());
        assertEquals(ChartHint.BAR, result.chartHint());
        assertEquals(1, result.rows().size());
        assertEquals("telemetry_events", runner.collection);
        assertEquals(5000L, runner.maxTimeMs);
        assertTrue(runner.pipeline.stream().anyMatch(stage -> stage.containsKey("$limit")));
        assertEquals(20, runner.pipeline.getLast().get("$limit"));
    }

    @Test
    void doesNotCallRunnerWhenAstIsInvalid() {
        RecordingRunner runner = new RecordingRunner(List.of());
        AnalyticsExecutor executor = executor(runner);

        assertThrows(AnalyticsValidationException.class, () -> executor.execute(new AnalyticsAst(
                "policies",
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                101,
                ChartHint.TABLE
        )));
        assertTrue(runner.pipeline.isEmpty());
    }

    private static AnalyticsExecutor executor(RecordingRunner runner) {
        return new AnalyticsExecutor(
                new AstValidator(AnalyticsTestSupport.CATALOG),
                new MongoPipelineCompiler(),
                new PipelineAllowlist(),
                runner,
                new AnalyticsProperties(5000L, 50, new AnalyticsProperties.Mongodb("")),
                AnalyticsTestSupport.CATALOG
        );
    }

    private static final class RecordingRunner implements MongoAggregationRunner {
        private final List<Document> rows;
        private String collection;
        private List<Document> pipeline = List.of();
        private long maxTimeMs;

        private RecordingRunner(List<Document> rows) {
            this.rows = rows;
        }

        @Override
        public List<Document> aggregate(String collection, List<Document> pipeline, long maxTimeMs) {
            this.collection = collection;
            this.pipeline = new ArrayList<>(pipeline);
            this.maxTimeMs = maxTimeMs;
            return rows;
        }
    }
}
