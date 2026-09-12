package com.example.ubi.analytics.compile;

import com.example.ubi.analytics.ast.AnalyticsAst;
import com.example.ubi.analytics.ast.FilterExpr;
import com.example.ubi.analytics.ast.MetricExpr;
import com.example.ubi.analytics.ast.SortDirection;
import com.example.ubi.analytics.ast.SortExpr;
import com.example.ubi.analytics.catalog.CollectionSchema;
import com.example.ubi.analytics.catalog.FieldSchema;
import com.example.ubi.analytics.catalog.FieldType;
import com.example.ubi.analytics.catalog.SchemaCatalog;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import org.bson.Document;
import org.springframework.stereotype.Component;

/**
 * Compiles a validated AST into a Mongo aggregation pipeline using only
 * {@code $match}, {@code $group}, {@code $sort}, {@code $limit}, {@code $project},
 * plus a minimal {@code $cond} inside {@code $group} for conditional sums.
 */
@Component
public class MongoPipelineCompiler {

    public List<Document> compile(AnalyticsAst ast, SchemaCatalog catalog) {
        CollectionSchema schema = catalog.requireCollection(ast.collection());
        List<Document> pipeline = new ArrayList<>();

        Document match = compileMatch(ast.filters(), schema, true);
        if (!match.isEmpty()) {
            pipeline.add(new Document("$match", match));
        }

        if (ast.aggregating()) {
            pipeline.add(new Document("$group", compileGroup(ast, schema)));
            pipeline.add(new Document("$project", compileGroupProject(ast, schema)));
            Document having = compileMatch(ast.having(), schema, false);
            if (!having.isEmpty()) {
                pipeline.add(new Document("$match", having));
            }
        } else {
            pipeline.add(new Document("$project", compileListingProject(schema)));
        }

        if (!ast.sort().isEmpty()) {
            pipeline.add(new Document("$sort", compileSort(ast.sort())));
        }

        pipeline.add(new Document("$limit", ast.limit()));
        return List.copyOf(pipeline);
    }

    private Document compileMatch(List<FilterExpr> filters, CollectionSchema schema, boolean remapToMongo) {
        Document match = new Document();
        for (FilterExpr filter : filters) {
            String key = remapToMongo ? schema.requireField(filter.field()).mongoName() : filter.field();
            Object predicate = toPredicate(filter, schema, remapToMongo);
            Object existing = match.get(key);
            if (existing instanceof Document existingDoc && predicate instanceof Document nextDoc) {
                existingDoc.putAll(nextDoc);
            } else {
                match.put(key, predicate);
            }
        }
        return match;
    }

    private Object toPredicate(FilterExpr filter, CollectionSchema schema, boolean remapToMongo) {
        Object converted = remapToMongo
                ? convertValue(schema.requireField(filter.field()), filter.value())
                : schema.field(filter.field())
                        .map(field -> convertValue(field, filter.value()))
                        .orElse(filter.value());
        return switch (filter.op()) {
            case EQ -> new Document("$eq", converted);
            case NEQ -> new Document("$ne", converted);
            case GT -> new Document("$gt", converted);
            case GTE -> new Document("$gte", converted);
            case LT -> new Document("$lt", converted);
            case LTE -> new Document("$lte", converted);
            case IN -> new Document("$in", converted);
            case EXISTS -> new Document("$exists", asBoolean(filter.value(), true));
        };
    }

    private Document compileGroup(AnalyticsAst ast, CollectionSchema schema) {
        Document group = new Document();
        if (ast.groupBy().isEmpty()) {
            group.put("_id", null);
        } else {
            Document id = new Document();
            for (String fieldName : ast.groupBy()) {
                FieldSchema field = schema.requireField(fieldName);
                id.put(field.name(), "$" + field.mongoName());
            }
            group.put("_id", id);
        }
        for (MetricExpr metric : ast.metrics()) {
            group.put(metric.alias(), compileAccumulator(metric, schema));
        }
        return group;
    }

    private Document compileAccumulator(MetricExpr metric, CollectionSchema schema) {
        Object input = accumulatorInput(metric, schema);
        if (metric.hasConditionalFilter()) {
            FieldSchema filterField = schema.requireField(metric.filterField());
            Object equals = convertValue(filterField, metric.filterEquals());
            Document cond = new Document("$cond", List.of(
                    new Document("$eq", List.of("$" + filterField.mongoName(), equals)),
                    input,
                    0
            ));
            return new Document("$sum", cond);
        }
        return switch (metric.fn()) {
            case COUNT -> new Document("$sum", 1);
            case SUM -> new Document("$sum", input);
            case AVG -> new Document("$avg", input);
            case MIN -> new Document("$min", input);
            case MAX -> new Document("$max", input);
        };
    }

    private Object accumulatorInput(MetricExpr metric, CollectionSchema schema) {
        if (metric.field() == null || metric.field().isBlank()) {
            return 1;
        }
        return "$" + schema.requireField(metric.field()).mongoName();
    }

    private Document compileGroupProject(AnalyticsAst ast, CollectionSchema schema) {
        Document project = new Document("_id", 0);
        for (String fieldName : ast.groupBy()) {
            String canonical = schema.requireField(fieldName).name();
            project.put(canonical, "$_id." + canonical);
        }
        for (MetricExpr metric : ast.metrics()) {
            project.put(metric.alias(), 1);
        }
        return project;
    }

    private Document compileListingProject(CollectionSchema schema) {
        Document project = new Document("_id", 0);
        for (FieldSchema field : schema.fields()) {
            if (!field.nlExposed()) {
                continue;
            }
            if (field.mongoName().equals(field.name())) {
                project.put(field.name(), 1);
            } else {
                project.put(field.name(), "$" + field.mongoName());
            }
        }
        return project;
    }

    private Document compileSort(List<SortExpr> sortExprs) {
        Document sortDocument = new Document();
        for (SortExpr sortExpr : sortExprs) {
            sortDocument.put(sortExpr.field(), sortExpr.direction() == SortDirection.DESC ? -1 : 1);
        }
        return sortDocument;
    }

    Object convertValue(FieldSchema field, Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Collection<?> items) {
            List<Object> converted = new ArrayList<>(items.size());
            for (Object item : items) {
                converted.add(convertScalar(field, item));
            }
            return converted;
        }
        return convertScalar(field, value);
    }

    private Object convertScalar(FieldSchema field, Object value) {
        if (value == null) {
            return null;
        }
        if (field.type() == FieldType.INSTANT) {
            if (value instanceof Instant instant) {
                return Date.from(instant);
            }
            if (value instanceof String raw) {
                return Date.from(Instant.parse(raw));
            }
        }
        if (field.type() == FieldType.ENUM && value instanceof String raw) {
            return raw.trim().toUpperCase(Locale.ROOT);
        }
        if (field.type() == FieldType.BOOLEAN) {
            return asBoolean(value, false);
        }
        return value;
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof String raw) {
            return Boolean.parseBoolean(raw);
        }
        return defaultValue;
    }
}
