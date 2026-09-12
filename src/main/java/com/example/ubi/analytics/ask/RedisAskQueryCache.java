package com.example.ubi.analytics.ask;

import com.example.ubi.config.AnalyticsProperties;
import com.example.ubi.dto.AnalyticsAskResponse;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis-backed ask cache. All Redis failures degrade to a miss / no-op write.
 */
public class RedisAskQueryCache implements AskQueryCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(RedisAskQueryCache.class);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;
    private final String keyPrefix;
    private final Duration ttl;

    public RedisAskQueryCache(
            StringRedisTemplate redis,
            ObjectMapper objectMapper,
            AnalyticsProperties properties
    ) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.keyPrefix = properties.cache().keyPrefix();
        this.ttl = properties.cache().ttl();
    }

    RedisAskQueryCache(StringRedisTemplate redis, ObjectMapper objectMapper, String keyPrefix, Duration ttl) {
        this.redis = redis;
        this.objectMapper = objectMapper;
        this.keyPrefix = keyPrefix;
        this.ttl = ttl;
    }

    @Override
    public Optional<AnalyticsAskResponse> get(String cacheKey) {
        if (cacheKey == null || cacheKey.isBlank()) {
            return Optional.empty();
        }
        try {
            String json = redis.opsForValue().get(redisKey(cacheKey));
            if (json == null || json.isBlank()) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(json, AnalyticsAskResponse.class));
        } catch (Exception exception) {
            LOGGER.warn("Analytics ask cache get failed; degrading: {}", exception.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public void put(String cacheKey, AnalyticsAskResponse response) {
        if (cacheKey == null || cacheKey.isBlank() || response == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(response);
            redis.opsForValue().set(redisKey(cacheKey), json, ttl);
        } catch (JsonProcessingException exception) {
            LOGGER.warn("Analytics ask cache serialize failed; skipping store: {}", exception.getMessage());
        } catch (Exception exception) {
            LOGGER.warn("Analytics ask cache put failed; degrading: {}", exception.getMessage());
        }
    }

    String redisKey(String cacheKey) {
        return keyPrefix + cacheKey;
    }
}
