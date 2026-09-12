package com.example.ubi.analytics.ast;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ubi.analytics.AnalyticsTestSupport;
import org.junit.jupiter.api.Test;

class AnalyticsAstJsonTest {

    @Test
    void roundTripsFixtureJson() throws Exception {
        AnalyticsAst original = AnalyticsTestSupport.loadAst("/analytics/telemetry-hard-brakes.json");
        String json = AnalyticsTestSupport.MAPPER.writeValueAsString(original);
        AnalyticsAst copy = AnalyticsTestSupport.MAPPER.readValue(json, AnalyticsAst.class);

        assertEquals("telemetry_events", copy.collection());
        assertEquals(FilterOp.GTE, copy.filters().getFirst().op());
        assertEquals(MetricFn.SUM, copy.metrics().get(2).fn());
        assertEquals(true, copy.metrics().get(2).filterEquals());
        assertEquals(ChartHint.BAR, copy.chartHint());
        assertTrue(json.contains("\"op\":\"gte\""));
        assertTrue(json.contains("\"fn\":\"sum\""));
        assertTrue(json.contains("\"chartHint\":\"bar\""));
    }
}
