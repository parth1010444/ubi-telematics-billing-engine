package com.example.ubi.analytics.ask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ubi.analytics.AnalyticsTestSupport;
import com.example.ubi.analytics.ast.AnalyticsAst;
import com.example.ubi.exception.AnalyticsLlmUnavailableException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class QueryPlannerTest {

    @Test
    void returnsAstWhenPlannerStatusOk() {
        AnalyticsLlmClient llm = mock(AnalyticsLlmClient.class);
        AnalyticsAst ast = AnalyticsTestSupport.loadAst("/analytics/telemetry-hard-brakes.json");
        when(llm.completeJson(anyString(), anyString(), eq(PlannerOutput.class)))
                .thenReturn(new PlannerOutput("ok", ast, null, null));

        PlannerResult result = new QueryPlanner(llm, AnalyticsTestSupport.CATALOG)
                .plan("hard brakes by policy", "en", null);

        assertFalse(result.needsClarification());
        assertEquals("telemetry_events", result.ast().collection());
    }

    @Test
    void returnsClarificationForMultiCollectionStyleOutput() {
        AnalyticsLlmClient llm = mock(AnalyticsLlmClient.class);
        when(llm.completeJson(anyString(), anyString(), eq(PlannerOutput.class)))
                .thenReturn(new PlannerOutput(
                        "needs_clarification",
                        null,
                        "Accrued unpaid premium spans policies and billing rows.",
                        java.util.List.of(
                                "Sum usageCharge from billing_usage_records by policyId",
                                "List policies.basePremium and policies.paidAmountCents"
                        )
                ));

        PlannerResult result = new QueryPlanner(llm, AnalyticsTestSupport.CATALOG)
                .plan("how much unpaid premium does each policy owe?", null, null);

        assertTrue(result.needsClarification());
        assertEquals("Accrued unpaid premium spans policies and billing rows.", result.message());
        assertEquals(2, result.suggestions().size());
    }

    @Test
    void treatsMissingAstAsClarification() {
        AnalyticsLlmClient llm = mock(AnalyticsLlmClient.class);
        when(llm.completeJson(anyString(), anyString(), eq(PlannerOutput.class)))
                .thenReturn(new PlannerOutput("ok", null, null, null));

        PlannerResult result = new QueryPlanner(llm, AnalyticsTestSupport.CATALOG)
                .plan("something vague", null, null);

        assertTrue(result.needsClarification());
    }

    @Test
    void feedsValidationErrorOnRepairPrompt() {
        AnalyticsLlmClient llm = mock(AnalyticsLlmClient.class);
        AnalyticsAst ast = AnalyticsTestSupport.loadAst("/analytics/policies-avg-premium.json");
        when(llm.completeJson(anyString(), anyString(), eq(PlannerOutput.class)))
                .thenReturn(new PlannerOutput("ok", ast, null, null));

        new QueryPlanner(llm, AnalyticsTestSupport.CATALOG)
                .plan("avg premium", "en", "collection is not allowlisted: users");

        ArgumentCaptor<String> user = ArgumentCaptor.forClass(String.class);
        verify(llm).completeJson(anyString(), user.capture(), eq(PlannerOutput.class));
        assertTrue(user.getValue().contains("validationErrors: collection is not allowlisted: users"));
        assertTrue(user.getValue().contains("locale: en"));
    }

    @Test
    void systemPromptForbidsLookupAndEncodesCatalog() {
        AnalyticsLlmClient llm = mock(AnalyticsLlmClient.class);
        when(llm.completeJson(anyString(), anyString(), eq(PlannerOutput.class)))
                .thenReturn(new PlannerOutput("needs_clarification", null, "no", java.util.List.of()));

        QueryPlanner planner = new QueryPlanner(llm, AnalyticsTestSupport.CATALOG);
        planner.plan("join policies to telemetry", null, null);

        ArgumentCaptor<String> system = ArgumentCaptor.forClass(String.class);
        verify(llm).completeJson(system.capture(), anyString(), eq(PlannerOutput.class));
        String prompt = system.getValue();
        assertTrue(prompt.contains("Never use $lookup"));
        assertTrue(prompt.contains("telemetry_events"));
        assertTrue(prompt.contains("nlExposed=false"));
        assertTrue(prompt.contains("stripeCustomerId"));
        assertTrue(prompt.contains("allowedFilterOps"));
    }

    @Test
    void unavailableClientSurfaces503Exception() {
        QueryPlanner planner = new QueryPlanner(new UnavailableAnalyticsLlmClient(), AnalyticsTestSupport.CATALOG);
        assertThrows(AnalyticsLlmUnavailableException.class, () -> planner.plan("hello", null, null));
    }

    @Test
    void parseFailureBecomesClarificationNotCrash() {
        AnalyticsLlmClient llm = mock(AnalyticsLlmClient.class);
        when(llm.completeJson(any(), any(), any())).thenThrow(new IllegalArgumentException("bad json"));

        PlannerResult result = new QueryPlanner(llm, AnalyticsTestSupport.CATALOG).plan("x", null, null);
        assertTrue(result.needsClarification());
    }
}
