package com.example.ubi.analytics.catalog;

/**
 * Documented relationship for later LLM planning. Phase A never joins collections
 * ({@code $lookup} is forbidden).
 */
public record RelationshipDoc(
        String field,
        String targetCollection,
        String targetField,
        String cardinality,
        String description
) {
}
