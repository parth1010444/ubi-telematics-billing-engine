package com.example.ubi.analytics.catalog;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public record CollectionSchema(
        String name,
        String domainClass,
        String description,
        List<FieldSchema> fields,
        List<RelationshipDoc> relationships
) {
    public CollectionSchema {
        fields = fields == null ? List.of() : List.copyOf(fields);
        relationships = relationships == null ? List.of() : List.copyOf(relationships);
        if (description == null) {
            description = "";
        }
        if (domainClass == null) {
            domainClass = "";
        }
    }

    public Optional<FieldSchema> field(String nameOrAlias) {
        if (nameOrAlias == null) {
            return Optional.empty();
        }
        return fields.stream().filter(field -> field.matches(nameOrAlias)).findFirst();
    }

    public FieldSchema requireField(String nameOrAlias) {
        return field(nameOrAlias).orElseThrow(() ->
                new IllegalArgumentException("Unknown field '" + nameOrAlias + "' on " + name));
    }

    public Map<String, FieldSchema> fieldsByName() {
        Map<String, FieldSchema> map = new LinkedHashMap<>();
        for (FieldSchema field : fields) {
            map.put(field.name(), field);
        }
        return Map.copyOf(map);
    }
}
