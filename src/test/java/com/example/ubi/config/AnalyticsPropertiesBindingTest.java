package com.example.ubi.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Regression: an extra constructor on this record made Spring Boot look for
 * {@code AnalyticsProperties()} and fail startup of {@code analyticsAskService}.
 */
class AnalyticsPropertiesBindingTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsCanonicalConstructorWithDefaults() {
        runner.run(context -> {
            assertTrue(context.getStartupFailure() == null, () -> {
                Throwable failure = context.getStartupFailure();
                return failure == null ? "" : failure.getMessage();
            });
            AnalyticsProperties properties = context.getBean(AnalyticsProperties.class);
            assertEquals(5000L, properties.maxTimeMs());
            assertEquals(50, properties.defaultLimit());
            assertTrue(properties.cache().isEnabled());
            assertEquals(3600L, properties.cache().ttlSeconds());
            assertEquals(8, properties.llm().summaryMaxRows());
        });
    }

    @Test
    void bindsNestedCacheAndLlm() {
        runner.withPropertyValues(
                "analytics.max-time-ms=2500",
                "analytics.cache.enabled=false",
                "analytics.cache.ttl-seconds=90",
                "analytics.llm.summary-max-rows=3"
        ).run(context -> {
            AnalyticsProperties properties = context.getBean(AnalyticsProperties.class);
            assertEquals(2500L, properties.maxTimeMs());
            assertEquals(false, properties.cache().isEnabled());
            assertEquals(90L, properties.cache().ttlSeconds());
            assertEquals(3, properties.llm().summaryMaxRows());
        });
    }

    @EnableConfigurationProperties(AnalyticsProperties.class)
    static class TestConfig {
    }
}
