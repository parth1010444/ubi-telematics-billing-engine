package com.example.ubi.analytics.ask;

import com.example.ubi.dto.AnalyticsAskResponse;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Process-local cache for tests. Not used in production.
 */
public class InMemoryAskQueryCache implements AskQueryCache {

    private final ConcurrentHashMap<String, AnalyticsAskResponse> store = new ConcurrentHashMap<>();

    @Override
    public Optional<AnalyticsAskResponse> get(String cacheKey) {
        if (cacheKey == null) {
            return Optional.empty();
        }
        return Optional.ofNullable(store.get(cacheKey));
    }

    @Override
    public void put(String cacheKey, AnalyticsAskResponse response) {
        if (cacheKey == null || response == null) {
            return;
        }
        store.put(cacheKey, response);
    }

    public void clear() {
        store.clear();
    }

    public int size() {
        return store.size();
    }
}
