package com.example.ubi.dto;

import com.example.ubi.analytics.catalog.CollectionSchema;
import com.example.ubi.analytics.catalog.FieldSchema;
import com.example.ubi.analytics.catalog.FieldType;
import com.example.ubi.analytics.catalog.RelationshipDoc;
import com.example.ubi.analytics.catalog.SchemaCatalog;
import java.util.List;

public record AnalyticsCatalogResponse(
        String version,
        List<CollectionView> collections
) {
    public static AnalyticsCatalogResponse from(SchemaCatalog catalog) {
        return new AnalyticsCatalogResponse(
                catalog.version(),
                catalog.collections().stream().map(CollectionView::from).toList()
        );
    }

    public record CollectionView(
            String name,
            String domainClass,
            String description,
            List<FieldView> fields,
            List<RelationshipDoc> relationships
    ) {
        static CollectionView from(CollectionSchema schema) {
            return new CollectionView(
                    schema.name(),
                    schema.domainClass(),
                    schema.description(),
                    schema.fields().stream().map(FieldView::from).toList(),
                    schema.relationships()
            );
        }
    }

    public record FieldView(
            String name,
            String mongoName,
            FieldType type,
            boolean nlExposed,
            String description,
            List<String> enumValues,
            List<String> aliases
    ) {
        static FieldView from(FieldSchema field) {
            return new FieldView(
                    field.name(),
                    field.mongoName(),
                    field.type(),
                    field.nlExposed(),
                    field.description(),
                    field.enumValues(),
                    field.aliases()
            );
        }
    }
}
