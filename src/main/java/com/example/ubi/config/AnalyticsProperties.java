package com.example.ubi.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analytics")
public record AnalyticsProperties(
        Long maxTimeMs,
        Integer defaultLimit,
        Mongodb mongodb,
        Cache cache,
        Llm llm
) {
    public static final long DEFAULT_MAX_TIME_MS = 5000L;
    public static final int DEFAULT_LIMIT = 50;
    public static final long DEFAULT_CACHE_TTL_SECONDS = 3600L;
    public static final String DEFAULT_CACHE_KEY_PREFIX = "analytics:ask:";
    public static final int DEFAULT_SUMMARY_MAX_ROWS = 8;
    public static final int DEFAULT_SUMMARY_MAX_VALUE_CHARS = 80;

    public AnalyticsProperties {
        if (maxTimeMs == null || maxTimeMs <= 0) {
            maxTimeMs = DEFAULT_MAX_TIME_MS;
        }
        if (defaultLimit == null || defaultLimit <= 0) {
            defaultLimit = DEFAULT_LIMIT;
        }
        if (mongodb == null) {
            mongodb = new Mongodb("");
        } else if (mongodb.uri() == null) {
            mongodb = new Mongodb("");
        }
        if (cache == null) {
            cache = new Cache(true, DEFAULT_CACHE_TTL_SECONDS, DEFAULT_CACHE_KEY_PREFIX);
        }
        if (llm == null) {
            llm = new Llm(DEFAULT_SUMMARY_MAX_ROWS, DEFAULT_SUMMARY_MAX_VALUE_CHARS);
        }
    }

    /**
     * Test helper. Spring Boot constructor-binds the canonical record constructor
     * only — do not add extra constructors or {@code @ConfigurationProperties} fails to start.
     */
    public static AnalyticsProperties of(Long maxTimeMs, Integer defaultLimit, Mongodb mongodb) {
        return new AnalyticsProperties(maxTimeMs, defaultLimit, mongodb, null, null);
    }

    public String mongodbUri() {
        return mongodb == null || mongodb.uri() == null ? "" : mongodb.uri().trim();
    }

    public record Mongodb(String uri) {
    }

    public record Cache(Boolean enabled, Long ttlSeconds, String keyPrefix) {
        public Cache {
            if (enabled == null) {
                enabled = true;
            }
            if (ttlSeconds == null || ttlSeconds <= 0) {
                ttlSeconds = DEFAULT_CACHE_TTL_SECONDS;
            }
            if (keyPrefix == null || keyPrefix.isBlank()) {
                keyPrefix = DEFAULT_CACHE_KEY_PREFIX;
            }
        }

        public Duration ttl() {
            return Duration.ofSeconds(ttlSeconds);
        }

        public boolean isEnabled() {
            return Boolean.TRUE.equals(enabled);
        }
    }

    public record Llm(Integer summaryMaxRows, Integer summaryMaxValueChars) {
        public Llm {
            if (summaryMaxRows == null || summaryMaxRows <= 0) {
                summaryMaxRows = DEFAULT_SUMMARY_MAX_ROWS;
            }
            if (summaryMaxValueChars == null || summaryMaxValueChars <= 0) {
                summaryMaxValueChars = DEFAULT_SUMMARY_MAX_VALUE_CHARS;
            }
        }
    }
}
