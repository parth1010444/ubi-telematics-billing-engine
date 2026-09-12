package com.example.ubi.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AnalyticsAskRequest(
        String question,
        Boolean skipCache,
        String locale
) {
    public boolean skipCacheRequested() {
        return Boolean.TRUE.equals(skipCache);
    }
}
