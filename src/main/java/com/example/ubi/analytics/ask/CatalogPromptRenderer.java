package com.example.ubi.analytics.ask;

import com.example.ubi.analytics.ast.ChartHint;
import com.example.ubi.analytics.ast.FilterOp;
import com.example.ubi.analytics.ast.MetricFn;
import com.example.ubi.analytics.catalog.CollectionSchema;
import com.example.ubi.analytics.catalog.FieldSchema;
import com.example.ubi.analytics.catalog.RelationshipDoc;
import com.example.ubi.analytics.catalog.SchemaCatalog;
import java.util.stream.Collectors;

/**
 * Compact catalog text for the planner system prompt.
 */
public final class CatalogPromptRenderer {

    private CatalogPromptRenderer() {
    }

    public static String render(SchemaCatalog catalog) {
        StringBuilder out = new StringBuilder();
        out.append("catalogVersion: ").append(catalog.version()).append('\n');
        out.append("allowedCollections: ")
                .append(String.join(", ", catalog.collectionNames()))
                .append('\n');
        out.append("allowedFilterOps: ").append(FilterOp.allowed()).append('\n');
        out.append("allowedMetricFns: ").append(MetricFn.allowed()).append('\n');
        out.append("allowedChartHints: ").append(ChartHint.allowed()).append('\n');
        out.append('\n');
        for (CollectionSchema collection : catalog.collections()) {
            out.append("collection ").append(collection.name()).append('\n');
            out.append("  description: ").append(collection.description()).append('\n');
            for (FieldSchema field : collection.fields()) {
                out.append("  field ").append(field.name())
                        .append(" type=").append(field.type())
                        .append(" nlExposed=").append(field.nlExposed());
                if (!field.enumValues().isEmpty()) {
                    out.append(" enum=[").append(String.join(",", field.enumValues())).append(']');
                }
                if (!field.aliases().isEmpty()) {
                    out.append(" aliases=[").append(String.join(",", field.aliases())).append(']');
                }
                if (!field.description().isBlank()) {
                    out.append(" — ").append(field.description());
                }
                out.append('\n');
            }
            for (RelationshipDoc rel : collection.relationships()) {
                out.append("  relationship ")
                        .append(rel.field()).append(" -> ")
                        .append(rel.targetCollection()).append('.')
                        .append(rel.targetField())
                        .append(" (").append(rel.cardinality()).append(") — ")
                        .append(rel.description())
                        .append('\n');
            }
            out.append('\n');
        }
        return out.toString();
    }

    public static String collectionList(SchemaCatalog catalog) {
        return catalog.collectionNames().stream().collect(Collectors.joining(", "));
    }
}
