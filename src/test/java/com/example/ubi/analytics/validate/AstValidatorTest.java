package com.example.ubi.analytics.validate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.example.ubi.analytics.AnalyticsTestSupport;
import com.example.ubi.analytics.ast.AnalyticsAst;
import com.example.ubi.analytics.ast.AnalyticsLimits;
import com.example.ubi.analytics.ast.ChartHint;
import com.example.ubi.analytics.ast.FilterExpr;
import com.example.ubi.analytics.ast.FilterOp;
import com.example.ubi.analytics.ast.MetricExpr;
import com.example.ubi.analytics.ast.MetricFn;
import com.example.ubi.analytics.ast.SortDirection;
import com.example.ubi.analytics.ast.SortExpr;
import com.example.ubi.exception.AnalyticsValidationException;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AstValidatorTest {

    private final AstValidator validator = new AstValidator(AnalyticsTestSupport.CATALOG);

    @Test
    void acceptsFixtureAstsAndDefaultsLimit() {
        AnalyticsAst telemetry = validator.validateAndNormalize(
                AnalyticsTestSupport.loadAst("/analytics/telemetry-hard-brakes.json")
        );
        assertEquals(20, telemetry.limit());
        assertEquals(ChartHint.BAR, telemetry.chartHint());

        AnalyticsAst listing = validator.validateAndNormalize(new AnalyticsAst(
                "telemetry_events",
                List.of(new FilterExpr("policyId", FilterOp.EQ, "pol_1")),
                List.of(),
                List.of(),
                List.of(),
                List.of(new SortExpr("timestamp", SortDirection.DESC)),
                null,
                ChartHint.TABLE
        ));
        assertEquals(AnalyticsLimits.DEFAULT_LIMIT, listing.limit());
    }

    @Test
    void rejectsUnknownCollection() {
        AnalyticsValidationException exception = assertThrows(
                AnalyticsValidationException.class,
                () -> validator.validateAndNormalize(emptyAst("$cmd", List.of(), List.of(), List.of()))
        );
        assertTrue(exception.getMessage().contains("collection is not allowed"));
    }

    @Test
    void rejectsUnknownAndSensitiveFields() {
        AnalyticsValidationException unknown = assertThrows(
                AnalyticsValidationException.class,
                () -> validator.validateAndNormalize(emptyAst(
                        "policies",
                        List.of(new FilterExpr("notAField", FilterOp.EQ, "x")),
                        List.of(),
                        List.of()
                ))
        );
        assertTrue(unknown.getMessage().contains("not a catalog field"));

        AnalyticsValidationException hidden = assertThrows(
                AnalyticsValidationException.class,
                () -> validator.validateAndNormalize(emptyAst(
                        "policies",
                        List.of(new FilterExpr("stripeCustomerId", FilterOp.EQ, "cus_x")),
                        List.of(),
                        List.of()
                ))
        );
        assertTrue(hidden.getMessage().contains("not NL-exposed"));

        AnalyticsValidationException checkout = assertThrows(
                AnalyticsValidationException.class,
                () -> validator.validateAndNormalize(emptyAst(
                        "policies",
                        List.of(new FilterExpr("lastCheckoutSessionId", FilterOp.EQ, "cs_x")),
                        List.of(),
                        List.of()
                ))
        );
        assertTrue(checkout.getMessage().contains("not NL-exposed"));
    }

    @Test
    void rejectsWriteLikeOperatorsEncodedInFieldOrValue() {
        AnalyticsValidationException where = assertThrows(
                AnalyticsValidationException.class,
                () -> validator.validateAndNormalize(emptyAst(
                        "policies",
                        List.of(new FilterExpr("$where", FilterOp.EQ, "1 == 1")),
                        List.of(),
                        List.of()
                ))
        );
        assertTrue(where.getMessage().contains("not a safe identifier"));

        AnalyticsValidationException injected = assertThrows(
                AnalyticsValidationException.class,
                () -> validator.validateAndNormalize(emptyAst(
                        "policies",
                        List.of(new FilterExpr("status", FilterOp.EQ, Map.of("$gt", ""))),
                        List.of(),
                        List.of()
                ))
        );
        assertTrue(injected.getMessage().contains("Mongo operators"));

        AnalyticsValidationException outAlias = assertThrows(
                AnalyticsValidationException.class,
                () -> validator.validateAndNormalize(new AnalyticsAst(
                        "policies",
                        List.of(),
                        List.of(),
                        List.of(new MetricExpr("$out", MetricFn.COUNT, null, null, null)),
                        List.of(),
                        List.of(),
                        10,
                        ChartHint.TABLE
                ))
        );
        assertTrue(outAlias.getMessage().contains("not a safe identifier"));
    }

    @Test
    void rejectsCardinalityAndLimitViolations() {
        List<FilterExpr> tooManyFilters = List.of(
                filter("status"), filter("userId"), filter("policyId"), filter("policyNumber"),
                filter("basePremium"), filter("paidAmountCents"), filter("lastPaidAt"),
                filter("status"), filter("userId")
        );
        AnalyticsValidationException filters = assertThrows(
                AnalyticsValidationException.class,
                () -> validator.validateAndNormalize(emptyAst("policies", tooManyFilters, List.of(), List.of()))
        );
        assertTrue(filters.getMessage().contains("filters exceeds max"));

        AnalyticsValidationException limit = assertThrows(
                AnalyticsValidationException.class,
                () -> validator.validateAndNormalize(new AnalyticsAst(
                        "policies",
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        List.of(),
                        101,
                        ChartHint.TABLE
                ))
        );
        assertTrue(limit.getMessage().contains("limit must be between"));
    }

    @Test
    void rejectsGroupByWithoutMetricsAndBadHaving() {
        AnalyticsValidationException groupOnly = assertThrows(
                AnalyticsValidationException.class,
                () -> validator.validateAndNormalize(new AnalyticsAst(
                        "policies",
                        List.of(),
                        List.of("status"),
                        List.of(),
                        List.of(),
                        List.of(),
                        10,
                        ChartHint.TABLE
                ))
        );
        assertTrue(groupOnly.getMessage().contains("groupBy requires at least one metric"));

        AnalyticsValidationException having = assertThrows(
                AnalyticsValidationException.class,
                () -> validator.validateAndNormalize(new AnalyticsAst(
                        "policies",
                        List.of(),
                        List.of("status"),
                        List.of(new MetricExpr("n", MetricFn.COUNT, null, null, null)),
                        List.of(new FilterExpr("basePremium", FilterOp.GT, 1)),
                        List.of(),
                        10,
                        ChartHint.TABLE
                ))
        );
        assertTrue(having.getMessage().contains("metric alias or groupBy field"));
    }

    private static AnalyticsAst emptyAst(
            String collection,
            List<FilterExpr> filters,
            List<String> groupBy,
            List<MetricExpr> metrics
    ) {
        return new AnalyticsAst(collection, filters, groupBy, metrics, List.of(), List.of(), 10, ChartHint.TABLE);
    }

    private static FilterExpr filter(String field) {
        return new FilterExpr(field, FilterOp.EXISTS, true);
    }
}
