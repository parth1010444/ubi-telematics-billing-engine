package com.example.ubi.analytics;

import com.example.ubi.analytics.ask.AnalyticsLlmClient;
import com.example.ubi.analytics.ask.AskQueryCache;
import com.example.ubi.analytics.ask.ChatClientAnalyticsLlmClient;
import com.example.ubi.analytics.ask.NoOpAskQueryCache;
import com.example.ubi.analytics.ask.RedisAskQueryCache;
import com.example.ubi.analytics.ask.UnavailableAnalyticsLlmClient;
import com.example.ubi.config.AnalyticsProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.core.StringRedisTemplate;

@Configuration
public class AnalyticsAskConfiguration {

    @Bean
    @ConditionalOnBean(ChatModel.class)
    @ConditionalOnMissingBean(ChatClient.class)
    ChatClient analyticsChatClient(ChatModel chatModel) {
        return ChatClient.create(chatModel);
    }

    @Bean
    @ConditionalOnBean(ChatClient.class)
    @ConditionalOnMissingBean(AnalyticsLlmClient.class)
    AnalyticsLlmClient chatClientAnalyticsLlmClient(ChatClient chatClient) {
        return new ChatClientAnalyticsLlmClient(chatClient);
    }

    @Bean
    @ConditionalOnMissingBean(AnalyticsLlmClient.class)
    AnalyticsLlmClient unavailableAnalyticsLlmClient() {
        return new UnavailableAnalyticsLlmClient();
    }

    @Bean
    AskQueryCache askQueryCache(
            ObjectProvider<StringRedisTemplate> redis,
            ObjectMapper objectMapper,
            AnalyticsProperties properties
    ) {
        StringRedisTemplate template = redis.getIfAvailable();
        if (!properties.cache().isEnabled() || template == null) {
            return new NoOpAskQueryCache();
        }
        return new RedisAskQueryCache(template, objectMapper, properties);
    }
}
