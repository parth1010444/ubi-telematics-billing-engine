package com.example.ubi.analytics.ask;

import com.example.ubi.dto.AnalyticsAskResponse;
import java.util.Optional;

public class NoOpAskQueryCache implements AskQueryCache {

    @Override
    public Optional<AnalyticsAskResponse> get(String cacheKey) {
        return Optional.empty();
    }

    @Override
    public void put(String cacheKey, AnalyticsAskResponse response) {
        // intentionally empty
    }
}
