package com.example.ubi.analytics;

import com.example.ubi.analytics.ast.AnalyticsAst;
import com.example.ubi.analytics.catalog.SchemaCatalog;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public final class AnalyticsTestSupport {

    public static final ObjectMapper MAPPER = JsonMapper.builder()
            .addModule(new JavaTimeModule())
            .build();

    public static final SchemaCatalog CATALOG = SchemaCatalog.builtin();

    private AnalyticsTestSupport() {
    }

    public static AnalyticsAst loadAst(String classpathResource) {
        try (InputStream input = AnalyticsTestSupport.class.getResourceAsStream(classpathResource)) {
            if (input == null) {
                throw new IllegalArgumentException("Missing test resource: " + classpathResource);
            }
            return MAPPER.readValue(input, AnalyticsAst.class);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }
}
