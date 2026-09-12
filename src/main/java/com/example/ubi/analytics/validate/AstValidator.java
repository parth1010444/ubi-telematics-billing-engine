package com.example.ubi.analytics.validate;

import com.example.ubi.analytics.ast.AnalyticsAst;
import com.example.ubi.analytics.ast.AnalyticsLimits;
import com.example.ubi.analytics.ast.FilterExpr;
import com.example.ubi.analytics.ast.FilterOp;
import com.example.ubi.analytics.ast.MetricExpr;
import com.example.ubi.analytics.ast.MetricFn;
import com.example.ubi.analytics.ast.SortExpr;
import com.example.ubi.analytics.catalog.CollectionSchema;
import com.example.ubi.analytics.catalog.FieldSchema;
import com.example.ubi.analytics.catalog.FieldType;
import com.example.ubi.analytics.catalog.SchemaCatalog;
import com.example.ubi.config.AnalyticsProperties;
import com.example.ubi.exception.AnalyticsValidationException;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Deny-by-default AST checks against the hand-maintained schema catalog.
 */
@Component
public class AstValidator {

    private static final Pattern SAFE_IDENTIFIER = Pattern.compile("^[A-Za-z][A-Za-z0-9_]*$");

    private final SchemaCatalog catalog;
    private final int defaultLimit;

    @Autowired
    public AstValidator(SchemaCatalog catalog, AnalyticsProperties properties) {
        this.catalog = catalog;
        this.defaultLimit = properties.defaultLimit();
    }

    public AstValidator(SchemaCatalog catalog) {
        this.catalog = catalog;
        this.defaultLimit = AnalyticsLimits.DEFAULT_LIMIT;
    }

    public AnalyticsAst validateAndNormalize(AnalyticsAst ast) {
        List<String> errors = new ArrayList<>();
        if (ast == null) {
            throw new AnalyticsValidationException(List.of("AST is required"));
        }

        Optional<CollectionSchema> collection = resolveCollection(ast.collection(), errors);
        validateListBounds(ast, errors);

        if (collection.isPresent()) {
            CollectionSchema schema = collection.get();
            validateFilters("filters", ast.filters(), schema, errors, true);
            validateGroupBy(ast.groupBy(), schema, errors);
            Set<String> metricAliases = validateMetrics(ast.metrics(), schema, errors);
            validateHaving(ast, schema, metricAliases, errors);
            validateSort(ast, schema, metricAliases, errors);
        }

        int limit = normalizeLimit(ast.limit(), errors);
        if (!errors.isEmpty()) {
            throw new AnalyticsValidationException(errors);
        }
        return ast.withLimit(limit);
    }

    private Optional<CollectionSchema> resolveCollection(String collection, List<String> errors) {
        if (collection == null || collection.isBlank()) {
            errors.add("collection is required");
            return Optional.empty();
        }
        if (collection.startsWith("$") || collection.contains(".") || !SAFE_IDENTIFIER.matcher(collection).matches()) {
            errors.add("collection is not allowed: " + collection);
            return Optional.empty();
        }
        Optional<CollectionSchema> schema = catalog.collection(collection);
        if (schema.isEmpty()) {
            errors.add("collection is not allowlisted: " + collection
                    + " (allowed: " + String.join(", ", catalog.collectionNames()) + ")");
        }
        return schema;
    }

    private void validateListBounds(AnalyticsAst ast, List<String> errors) {
        if (ast.filters().size() > AnalyticsLimits.MAX_FILTERS) {
            errors.add("filters exceeds max of " + AnalyticsLimits.MAX_FILTERS);
        }
        if (ast.groupBy().size() > AnalyticsLimits.MAX_GROUP_BY) {
            errors.add("groupBy exceeds max of " + AnalyticsLimits.MAX_GROUP_BY);
        }
        if (ast.metrics().size() > AnalyticsLimits.MAX_METRICS) {
            errors.add("metrics exceeds max of " + AnalyticsLimits.MAX_METRICS);
        }
        if (ast.sort().size() > AnalyticsLimits.MAX_SORT) {
            errors.add("sort exceeds max of " + AnalyticsLimits.MAX_SORT);
        }
        if (ast.having().size() > AnalyticsLimits.MAX_HAVING) {
            errors.add("having exceeds max of " + AnalyticsLimits.MAX_HAVING);
        }
        if (!ast.groupBy().isEmpty() && ast.metrics().isEmpty()) {
            errors.add("groupBy requires at least one metric");
        }
        if (!ast.having().isEmpty() && ast.metrics().isEmpty()) {
            errors.add("having is only allowed when metrics are present");
        }
        if (containsNull(ast.filters()) || containsNull(ast.groupBy())
                || containsNull(ast.metrics()) || containsNull(ast.having())
                || containsNull(ast.sort())) {
            errors.add("filters, groupBy, metrics, having, and sort must not contain null entries");
        }
    }

    private void validateFilters(
            String path,
            List<FilterExpr> filters,
            CollectionSchema schema,
            List<String> errors,
            boolean requireCatalogField
    ) {
        for (int i = 0; i < filters.size(); i++) {
            FilterExpr filter = filters.get(i);
            if (filter == null) {
                continue;
            }
            String prefix = path + "[" + i + "]";
            if (filter.op() == null) {
                errors.add(prefix + ".op is required (allowed: " + FilterOp.allowed() + ")");
            }
            if (requireCatalogField) {
                Optional<FieldSchema> field = resolveExposedField(schema, filter.field(), prefix + ".field", errors);
                if (field.isPresent() && filter.op() != null) {
                    validateFilterValue(prefix, filter, field.get(), errors);
                }
            } else if (isUnsafeIdentifier(filter.field())) {
                errors.add(prefix + ".field is not a safe identifier: " + filter.field());
            }
        }
    }

    private void validateGroupBy(List<String> groupBy, CollectionSchema schema, List<String> errors) {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < groupBy.size(); i++) {
            String field = groupBy.get(i);
            if (field == null) {
                continue;
            }
            Optional<FieldSchema> resolved = resolveExposedField(schema, field, "groupBy[" + i + "]", errors);
            if (resolved.isPresent() && !seen.add(resolved.get().name())) {
                errors.add("groupBy[" + i + "] duplicates " + resolved.get().name());
            }
        }
    }

    private Set<String> validateMetrics(List<MetricExpr> metrics, CollectionSchema schema, List<String> errors) {
        Set<String> aliases = new HashSet<>();
        for (int i = 0; i < metrics.size(); i++) {
            MetricExpr metric = metrics.get(i);
            if (metric == null) {
                continue;
            }
            String prefix = "metrics[" + i + "]";
            if (metric.fn() == null) {
                errors.add(prefix + ".fn is required (allowed: " + MetricFn.allowed() + ")");
            }
            if (metric.alias() == null || metric.alias().isBlank()) {
                errors.add(prefix + ".alias is required");
            } else if (isUnsafeIdentifier(metric.alias()) || "_id".equals(metric.alias())) {
                errors.add(prefix + ".alias is not a safe identifier: " + metric.alias());
            } else if (!aliases.add(metric.alias())) {
                errors.add(prefix + ".alias is duplicated: " + metric.alias());
            }

            boolean needsField = metric.fn() != null && metric.fn().requiresField() && !metric.hasConditionalFilter();
            boolean hasField = metric.field() != null && !metric.field().isBlank();
            if (needsField && !hasField) {
                errors.add(prefix + ".field is required for fn=" + metric.fn().toJson());
            }
            if (hasField) {
                Optional<FieldSchema> field = resolveExposedField(schema, metric.field(), prefix + ".field", errors);
                if (field.isPresent() && metric.fn() != null && metric.fn() != MetricFn.COUNT) {
                    validateMetricFieldType(prefix, metric.fn(), field.get(), errors);
                }
            }
            if (metric.hasConditionalFilter()) {
                resolveExposedField(schema, metric.filterField(), prefix + ".filterField", errors);
            }
        }
        return aliases;
    }

    private void validateHaving(
            AnalyticsAst ast,
            CollectionSchema schema,
            Set<String> metricAliases,
            List<String> errors
    ) {
        for (int i = 0; i < ast.having().size(); i++) {
            FilterExpr filter = ast.having().get(i);
            if (filter == null) {
                continue;
            }
            String prefix = "having[" + i + "]";
            if (filter.op() == null) {
                errors.add(prefix + ".op is required (allowed: " + FilterOp.allowed() + ")");
            }
            if (filter.field() == null || filter.field().isBlank()) {
                errors.add(prefix + ".field is required");
                continue;
            }
            if (isUnsafeIdentifier(filter.field())) {
                errors.add(prefix + ".field is not a safe identifier: " + filter.field());
                continue;
            }
            boolean knownAlias = metricAliases.contains(filter.field());
            boolean knownGroup = ast.groupBy().stream()
                    .anyMatch(group -> schema.field(group).map(field -> field.matches(filter.field())).orElse(false)
                            || filter.field().equals(group));
            if (!knownAlias && !knownGroup) {
                errors.add(prefix + ".field must be a metric alias or groupBy field: " + filter.field());
            }
            if (filter.op() != null) {
                validateLiteralValue(prefix + ".value", filter.op(), filter.value(), errors);
            }
        }
    }

    private void validateSort(
            AnalyticsAst ast,
            CollectionSchema schema,
            Set<String> metricAliases,
            List<String> errors
    ) {
        for (int i = 0; i < ast.sort().size(); i++) {
            SortExpr sort = ast.sort().get(i);
            if (sort == null) {
                continue;
            }
            String prefix = "sort[" + i + "]";
            if (sort.field() == null || sort.field().isBlank()) {
                errors.add(prefix + ".field is required");
                continue;
            }
            if (isUnsafeIdentifier(sort.field())) {
                errors.add(prefix + ".field is not a safe identifier: " + sort.field());
                continue;
            }
            if (ast.aggregating()) {
                boolean knownAlias = metricAliases.contains(sort.field());
                boolean knownGroup = ast.groupBy().stream()
                        .anyMatch(group -> schema.field(group).map(field -> field.matches(sort.field())).orElse(false)
                                || sort.field().equals(group));
                if (!knownAlias && !knownGroup) {
                    errors.add(prefix + ".field must be a metric alias or groupBy field when aggregating: "
                            + sort.field());
                }
            } else {
                resolveExposedField(schema, sort.field(), prefix + ".field", errors);
            }
        }
    }

    private Optional<FieldSchema> resolveExposedField(
            CollectionSchema schema,
            String name,
            String path,
            List<String> errors
    ) {
        if (name == null || name.isBlank()) {
            errors.add(path + " is required");
            return Optional.empty();
        }
        if (isUnsafeIdentifier(name) && !"_id".equals(name)) {
            errors.add(path + " is not a safe identifier: " + name);
            return Optional.empty();
        }
        Optional<FieldSchema> field = schema.field(name);
        if (field.isEmpty()) {
            errors.add(path + " is not a catalog field on " + schema.name() + ": " + name);
            return Optional.empty();
        }
        if (!field.get().nlExposed()) {
            errors.add(path + " is not NL-exposed (sensitive): " + field.get().name());
            return Optional.empty();
        }
        return field;
    }

    private void validateFilterValue(String prefix, FilterExpr filter, FieldSchema field, List<String> errors) {
        validateLiteralValue(prefix + ".value", filter.op(), filter.value(), errors);
        if (filter.op() == FilterOp.EXISTS) {
            return;
        }
        if (field.type() == FieldType.INSTANT) {
            validateInstantValue(prefix + ".value", filter.op(), filter.value(), errors);
        }
        if (field.type() == FieldType.ENUM) {
            validateEnumValue(prefix + ".value", filter.op(), filter.value(), field, errors);
        }
    }

    private void validateLiteralValue(String path, FilterOp op, Object value, List<String> errors) {
        if (op == FilterOp.EXISTS) {
            if (value == null) {
                return;
            }
            if (!(value instanceof Boolean) && !isBooleanString(value)) {
                errors.add(path + " for exists must be a boolean");
            }
            return;
        }
        if (value == null) {
            errors.add(path + " is required for op=" + op.toJson());
            return;
        }
        if (containsMongoOperator(value)) {
            errors.add(path + " must not contain Mongo operators (write-like / injection)");
            return;
        }
        if (op == FilterOp.IN) {
            if (!(value instanceof Collection<?> items)) {
                errors.add(path + " for op=in must be an array");
                return;
            }
            if (items.isEmpty()) {
                errors.add(path + " for op=in must not be empty");
            }
            if (items.size() > AnalyticsLimits.MAX_IN_VALUES) {
                errors.add(path + " for op=in exceeds max of " + AnalyticsLimits.MAX_IN_VALUES + " values");
            }
            for (Object item : items) {
                if (item instanceof Map || item instanceof Collection) {
                    errors.add(path + " for op=in must contain only scalars");
                    break;
                }
                if (containsMongoOperator(item)) {
                    errors.add(path + " must not contain Mongo operators (write-like / injection)");
                    break;
                }
            }
            return;
        }
        if (value instanceof Map || value instanceof Collection) {
            errors.add(path + " must be a scalar");
        }
    }

    private void validateInstantValue(String path, FilterOp op, Object value, List<String> errors) {
        if (value == null || op == FilterOp.EXISTS) {
            return;
        }
        if (op == FilterOp.IN && value instanceof Collection<?> items) {
            for (Object item : items) {
                parseInstant(path, item, errors);
            }
            return;
        }
        parseInstant(path, value, errors);
    }

    private void parseInstant(String path, Object value, List<String> errors) {
        if (value instanceof Instant) {
            return;
        }
        if (!(value instanceof String raw)) {
            errors.add(path + " must be an ISO-8601 timestamp string");
            return;
        }
        try {
            Instant.parse(raw);
        } catch (DateTimeParseException ex) {
            errors.add(path + " is not a valid ISO-8601 timestamp: " + raw);
        }
    }

    private void validateEnumValue(
            String path,
            FilterOp op,
            Object value,
            FieldSchema field,
            List<String> errors
    ) {
        if (value == null || op == FilterOp.EXISTS) {
            return;
        }
        if (op == FilterOp.IN && value instanceof Collection<?> items) {
            for (Object item : items) {
                if (!field.isAllowedEnumValue(item)) {
                    errors.add(path + " is not an allowed enum value on " + field.name()
                            + " (allowed: " + String.join(", ", field.enumValues()) + ")");
                }
            }
            return;
        }
        if (!field.isAllowedEnumValue(value)) {
            errors.add(path + " is not an allowed enum value on " + field.name()
                    + " (allowed: " + String.join(", ", field.enumValues()) + ")");
        }
    }

    private void validateMetricFieldType(String prefix, MetricFn fn, FieldSchema field, List<String> errors) {
        if (fn == MetricFn.SUM && (field.isNumeric() || field.type() == FieldType.BOOLEAN)) {
            return;
        }
        if ((fn == MetricFn.AVG) && field.isNumeric()) {
            return;
        }
        if ((fn == MetricFn.MIN || fn == MetricFn.MAX)
                && (field.isNumeric() || field.type() == FieldType.INSTANT)) {
            return;
        }
        errors.add(prefix + ".field type " + field.type() + " cannot be used with fn=" + fn.toJson());
    }

    private int normalizeLimit(Integer limit, List<String> errors) {
        int resolved = limit == null ? defaultLimit : limit;
        if (resolved < AnalyticsLimits.MIN_LIMIT || resolved > AnalyticsLimits.MAX_LIMIT) {
            errors.add("limit must be between " + AnalyticsLimits.MIN_LIMIT
                    + " and " + AnalyticsLimits.MAX_LIMIT + " (got " + resolved + ")");
        }
        return resolved;
    }

    private static boolean isUnsafeIdentifier(String name) {
        if (name == null || name.isBlank()) {
            return true;
        }
        return name.indexOf('$') >= 0 || name.indexOf('.') >= 0 || !SAFE_IDENTIFIER.matcher(name).matches();
    }

    private static boolean containsMongoOperator(Object value) {
        if (value instanceof Map<?, ?> map) {
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (String.valueOf(entry.getKey()).startsWith("$") || containsMongoOperator(entry.getValue())) {
                    return true;
                }
            }
        } else if (value instanceof Collection<?> items) {
            for (Object item : items) {
                if (containsMongoOperator(item)) {
                    return true;
                }
            }
        } else if (value instanceof String raw && raw.startsWith("$")) {
            return true;
        }
        return false;
    }

    private static boolean isBooleanString(Object value) {
        if (!(value instanceof String raw)) {
            return false;
        }
        return "true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw);
    }

    private static boolean containsNull(List<?> values) {
        return values.stream().anyMatch(item -> item == null);
    }
}
