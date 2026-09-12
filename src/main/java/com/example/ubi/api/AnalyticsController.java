package com.example.ubi.api;

import com.example.ubi.analytics.ast.AnalyticsAst;
import com.example.ubi.analytics.catalog.SchemaCatalog;
import com.example.ubi.analytics.execute.AnalyticsExecutor;
import com.example.ubi.dto.AnalyticsCatalogResponse;
import com.example.ubi.dto.AnalyticsExecuteResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Phase A internal/test API. Accepts a pre-built analytics AST — not natural language.
 * {@code POST /api/v1/ask} arrives in Phase B.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsExecutor analyticsExecutor;
    private final SchemaCatalog schemaCatalog;

    public AnalyticsController(AnalyticsExecutor analyticsExecutor, SchemaCatalog schemaCatalog) {
        this.analyticsExecutor = analyticsExecutor;
        this.schemaCatalog = schemaCatalog;
    }

    @GetMapping("/catalog")
    public AnalyticsCatalogResponse catalog() {
        return AnalyticsCatalogResponse.from(schemaCatalog);
    }

    @PostMapping("/execute")
    public AnalyticsExecuteResponse execute(@RequestBody AnalyticsAst ast) {
        return AnalyticsExecuteResponse.from(analyticsExecutor.execute(ast));
    }
}
