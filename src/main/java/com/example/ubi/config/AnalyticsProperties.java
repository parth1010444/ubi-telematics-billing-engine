package com.example.ubi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "analytics")
public record AnalyticsProperties(
        Long maxTimeMs,
        Integer defaultLimit,
        Mongodb mongodb
) {
    public static final long DEFAULT_MAX_TIME_MS = 5000L;
    public static final int DEFAULT_LIMIT = 50;

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
    }

    public String mongodbUri() {
        return mongodb == null || mongodb.uri() == null ? "" : mongodb.uri().trim();
    }

    public record Mongodb(String uri) {
    }
}
