package com.example.ubi.api;

import com.example.ubi.analytics.ask.AnalyticsAskService;
import com.example.ubi.analytics.ast.AnalyticsAst;
import com.example.ubi.analytics.catalog.SchemaCatalog;
import com.example.ubi.analytics.execute.AnalyticsExecutor;
import com.example.ubi.dto.AnalyticsAskRequest;
import com.example.ubi.dto.AnalyticsAskResponse;
import com.example.ubi.dto.AnalyticsCatalogResponse;
import com.example.ubi.dto.AnalyticsExecuteResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Analytics API. {@code /execute} is AST-in (Phase A). {@code /ask} is natural language (Phase B).
 * {@code /ask} is unauthenticated in v1.
 */
@RestController
@RequestMapping("/api/v1/analytics")
public class AnalyticsController {

    private final AnalyticsExecutor analyticsExecutor;
    private final SchemaCatalog schemaCatalog;
    private final AnalyticsAskService analyticsAskService;

    public AnalyticsController(
            AnalyticsExecutor analyticsExecutor,
            SchemaCatalog schemaCatalog,
            AnalyticsAskService analyticsAskService
    ) {
        this.analyticsExecutor = analyticsExecutor;
        this.schemaCatalog = schemaCatalog;
        this.analyticsAskService = analyticsAskService;
    }

    @GetMapping("/catalog")
    public AnalyticsCatalogResponse catalog() {
        return AnalyticsCatalogResponse.from(schemaCatalog);
    }

    @PostMapping("/execute")
    public AnalyticsExecuteResponse execute(@RequestBody AnalyticsAst ast) {
        return AnalyticsExecuteResponse.from(analyticsExecutor.execute(ast));
    }

    @PostMapping("/ask")
    public AnalyticsAskResponse ask(@RequestBody AnalyticsAskRequest request) {
        return analyticsAskService.ask(request);
    }
}
