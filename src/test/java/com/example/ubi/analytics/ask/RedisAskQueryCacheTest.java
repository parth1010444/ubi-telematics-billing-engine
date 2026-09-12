package com.example.ubi.analytics.ask;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.example.ubi.analytics.AnalyticsTestSupport;
import com.example.ubi.analytics.ast.ChartHint;
import com.example.ubi.dto.AnalyticsAskMeta;
import com.example.ubi.dto.AnalyticsAskResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class RedisAskQueryCacheTest {

    @Test
    void getReturnsEmptyWhenRedisIsDown() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        when(redis.opsForValue()).thenThrow(new RuntimeException("Connection refused"));
        RedisAskQueryCache cache = cache(redis);

        assertTrue(cache.get("abc").isEmpty());
    }

    @Test
    void putDoesNotThrowWhenRedisIsDown() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        doThrow(new RuntimeException("Connection refused")).when(ops).set(anyString(), anyString(), any(Duration.class));

        cache(redis).put("abc", sampleResponse());
    }

    @Test
    @SuppressWarnings("unchecked")
    void getDeserializesStoredJson() throws Exception {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);
        AnalyticsAskResponse stored = sampleResponse();
        when(ops.get("analytics:ask:abc")).thenReturn(AnalyticsTestSupport.MAPPER.writeValueAsString(stored));

        AnalyticsAskResponse loaded = cache(redis).get("abc").orElseThrow();
        assertEquals("ok", loaded.status());
        assertEquals("two policies", loaded.summary());
        assertEquals(ChartHint.BAR, loaded.chartHint());
        assertEquals(List.of("policyId"), loaded.columns());
    }

    @Test
    @SuppressWarnings("unchecked")
    void putWritesPrefixedKeyWithTtl() {
        StringRedisTemplate redis = mock(StringRedisTemplate.class);
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        when(redis.opsForValue()).thenReturn(ops);

        AnalyticsAskResponse response = sampleResponse();
        cache(redis).put("abc", response);

        verify(ops).set(eq("analytics:ask:abc"), anyString(), eq(Duration.ofSeconds(3600)));
    }

    private static RedisAskQueryCache cache(StringRedisTemplate redis) {
        return new RedisAskQueryCache(
                redis,
                AnalyticsTestSupport.MAPPER,
                "analytics:ask:",
                Duration.ofSeconds(3600)
        );
    }

    private static AnalyticsAskResponse sampleResponse() {
        return new AnalyticsAskResponse(
                "ok",
                "two policies",
                ChartHint.BAR,
                List.of("policyId"),
                List.of(Map.of("policyId", "p1")),
                AnalyticsTestSupport.loadAst("/analytics/policies-avg-premium.json"),
                new AnalyticsAskMeta(false, 12L, false, "policies", "2026-09-12.1", 1)
        );
    }
}
