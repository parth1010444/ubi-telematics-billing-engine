package com.example.ubi.analytics.execute;

import com.example.ubi.analytics.allowlist.PipelineAllowlist;
import com.example.ubi.analytics.ast.AnalyticsAst;
import com.example.ubi.analytics.catalog.SchemaCatalog;
import com.example.ubi.analytics.compile.MongoPipelineCompiler;
import com.example.ubi.analytics.validate.AstValidator;
import com.example.ubi.config.AnalyticsProperties;
import com.example.ubi.exception.AnalyticsExecutionException;
import java.util.ArrayList;
import java.util.List;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsExecutor {

    private static final Logger LOGGER = LoggerFactory.getLogger(AnalyticsExecutor.class);

    private final AstValidator validator;
    private final MongoPipelineCompiler compiler;
    private final PipelineAllowlist allowlist;
    private final MongoAggregationRunner runner;
    private final AnalyticsProperties properties;
    private final SchemaCatalog catalog;

    public AnalyticsExecutor(
            AstValidator validator,
            MongoPipelineCompiler compiler,
            PipelineAllowlist allowlist,
            MongoAggregationRunner runner,
            AnalyticsProperties properties,
            SchemaCatalog catalog
    ) {
        this.validator = validator;
        this.compiler = compiler;
        this.allowlist = allowlist;
        this.runner = runner;
        this.properties = properties;
        this.catalog = catalog;
    }

    public AnalyticsQueryResult execute(AnalyticsAst ast) {
        AnalyticsAst normalized = validator.validateAndNormalize(ast);
        List<Document> pipeline = new ArrayList<>(compiler.compile(normalized, catalog));
        ensureLimit(pipeline, normalized.limit());
        allowlist.verify(pipeline);

        LOGGER.info(
                "Executing analytics pipeline collection={} stages={} catalogVersion={} maxTimeMs={}",
                normalized.collection(),
                pipeline.stream().map(stage -> stage.keySet().iterator().next()).toList(),
                catalog.version(),
                properties.maxTimeMs()
        );

        try {
            List<Document> rows = runner.aggregate(
                    normalized.collection(),
                    pipeline,
                    properties.maxTimeMs()
            );
            return new AnalyticsQueryResult(
                    catalog.version(),
                    normalized.collection(),
                    normalized.chartHint(),
                    rows
            );
        } catch (RuntimeException exception) {
            throw new AnalyticsExecutionException(
                    "Analytics query failed: " + exception.getMessage(),
                    exception
            );
        }
    }

    private void ensureLimit(List<Document> pipeline, int limit) {
        boolean hasLimit = pipeline.stream().anyMatch(stage -> stage.containsKey("$limit"));
        if (!hasLimit) {
            pipeline.add(new Document("$limit", limit));
        }
    }
}
