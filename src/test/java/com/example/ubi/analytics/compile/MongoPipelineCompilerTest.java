package com.example.ubi.analytics.compile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ubi.analytics.AnalyticsTestSupport;
import com.example.ubi.analytics.ast.AnalyticsAst;
import com.example.ubi.analytics.validate.AstValidator;
import java.util.Date;
import java.util.List;
import org.bson.Document;
import org.junit.jupiter.api.Test;

class MongoPipelineCompilerTest {

    private final AstValidator validator = new AstValidator(AnalyticsTestSupport.CATALOG);
    private final MongoPipelineCompiler compiler = new MongoPipelineCompiler();

    @Test
    void compilesTelemetryHardBrakeFixture() {
        List<Document> pipeline = compile("/analytics/telemetry-hard-brakes.json");
        assertEquals(List.of("$match", "$group", "$project", "$match", "$sort", "$limit"), stageNames(pipeline));

        Document match = pipeline.get(0).get("$match", Document.class);
        assertEquals(100, match.get("speedKmh", Document.class).get("$gte"));
        assertInstanceOf(Date.class, match.get("timestamp", Document.class).get("$gte"));

        Document group = pipeline.get(1).get("$group", Document.class);
        assertEquals("$policyId", group.get("_id", Document.class).get("policyId"));
        assertEquals(1, group.get("events", Document.class).get("$sum"));
        assertEquals("$distanceTraveledKm", group.get("totalDistance", Document.class).get("$sum"));

        Document hardBrakes = group.get("hardBrakes", Document.class);
        @SuppressWarnings("unchecked")
        List<Object> condArgs = (List<Object>) hardBrakes.get("$sum", Document.class).get("$cond");
        assertEquals(3, condArgs.size());
        assertEquals("$isHardBraking", condArgs.get(1));
        assertEquals(0, condArgs.get(2));

        Document project = pipeline.get(2).get("$project", Document.class);
        assertEquals(0, project.get("_id"));
        assertEquals("$_id.policyId", project.get("policyId"));
        assertEquals(1, project.get("hardBrakes"));

        assertEquals(5, pipeline.get(3).get("$match", Document.class).get("events", Document.class).get("$gt"));
        assertEquals(-1, pipeline.get(4).get("$sort", Document.class).get("hardBrakes"));
        assertEquals(20, pipeline.get(5).get("$limit"));
    }

    @Test
    void compilesOverallPolicyAverageWithoutGroupBy() {
        List<Document> pipeline = compile("/analytics/policies-avg-premium.json");
        assertEquals(List.of("$match", "$group", "$project", "$limit"), stageNames(pipeline));

        Document match = pipeline.get(0).get("$match", Document.class);
        assertEquals("ACTIVE", match.get("status", Document.class).get("$eq"));

        Document group = pipeline.get(1).get("$group", Document.class);
        assertNull(group.get("_id"));
        assertEquals("$basePremium", group.get("avgPremium", Document.class).get("$avg"));
        assertEquals(1, group.get("policyCount", Document.class).get("$sum"));
        assertEquals(1, pipeline.get(3).get("$limit"));
    }

    @Test
    void compilesBillingStatusBreakdown() {
        List<Document> pipeline = compile("/analytics/billing-by-status.json");
        assertEquals(List.of("$match", "$group", "$project", "$sort", "$limit"), stageNames(pipeline));

        @SuppressWarnings("unchecked")
        List<Object> statuses = (List<Object>) pipeline.get(0)
                .get("$match", Document.class)
                .get("status", Document.class)
                .get("$in");
        assertEquals(List.of("PENDING", "FAILED"), statuses);
        assertEquals("$status", pipeline.get(1).get("$group", Document.class).get("_id", Document.class).get("status"));
        assertEquals(10, pipeline.get(4).get("$limit"));
    }

    @Test
    void listingQueryProjectsOnlyNlExposedFields() {
        AnalyticsAst listing = validator.validateAndNormalize(
                AnalyticsTestSupport.MAPPER.convertValue(
                        java.util.Map.of(
                                "collection", "policies",
                                "filters", List.of(java.util.Map.of("field", "status", "op", "eq", "value", "ACTIVE")),
                                "limit", 5,
                                "chartHint", "table"
                        ),
                        AnalyticsAst.class
                )
        );
        List<Document> pipeline = compiler.compile(listing, AnalyticsTestSupport.CATALOG);
        Document project = pipeline.get(1).get("$project", Document.class);
        assertEquals(0, project.get("_id"));
        assertEquals("$_id", project.get("policyId"));
        assertEquals(1, project.get("userId"));
        assertTrue(!project.containsKey("stripeCustomerId"));
        assertTrue(!project.containsKey("lastCheckoutSessionId"));
        assertEquals(5, pipeline.get(2).get("$limit"));
    }

    private List<Document> compile(String resource) {
        AnalyticsAst ast = validator.validateAndNormalize(AnalyticsTestSupport.loadAst(resource));
        return compiler.compile(ast, AnalyticsTestSupport.CATALOG);
    }

    private static List<String> stageNames(List<Document> pipeline) {
        return pipeline.stream().map(stage -> stage.keySet().iterator().next()).toList();
    }
}
