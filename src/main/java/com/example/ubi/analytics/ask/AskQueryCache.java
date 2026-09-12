package com.example.ubi.analytics.ask;

import com.example.ubi.dto.AnalyticsAskResponse;
import java.util.Optional;

public interface AskQueryCache {

    Optional<AnalyticsAskResponse> get(String cacheKey);

    void put(String cacheKey, AnalyticsAskResponse response);
}
